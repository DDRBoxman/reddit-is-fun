package com.andrewshu.android.reddit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.app.Activity;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost.OnTabChangeListener;

public class SubmitLinkActivity extends TabActivity {
	
	private static final String TAG = "SubmitLinkActivity";

	TabHost mTabHost;
	
	private RedditSettings mSettings = new RedditSettings();
	private final DefaultHttpClient mClient = Common.createGzipHttpClient();
	
	private String mModhash = null;
	private String mSubmitUrl;
	private volatile String mCaptchaIden = null;
	private volatile String mCaptchaUrl = null;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		Common.loadRedditPreferences(this, mSettings, mClient);
		setTheme(mSettings.themeResId);
		
		setContentView(R.layout.submit_link_main);

		final FrameLayout fl = (FrameLayout) findViewById(android.R.id.tabcontent);
		if (mSettings.theme == Constants.THEME_LIGHT) {
			fl.setBackgroundResource(R.color.light_gray);
		} else {
			fl.setBackgroundResource(R.color.android_dark_background);
		}
		
		if (!mSettings.loggedIn) {
			Intent loginRequired = new Intent();
			setResult(Constants.RESULT_LOGIN_REQUIRED, loginRequired);
			finish();
		}
		
		mTabHost = getTabHost();
		mTabHost.addTab(mTabHost.newTabSpec(Constants.TAB_LINK).setIndicator("link").setContent(R.id.submit_link_view));
		mTabHost.addTab(mTabHost.newTabSpec(Constants.TAB_TEXT).setIndicator("text").setContent(R.id.submit_text_view));
		mTabHost.setOnTabChangedListener(new OnTabChangeListener() {
			public void onTabChanged(String tabId) {
				// Copy everything (except url and text) from old tab to new tab
				final EditText submitLinkTitle = (EditText) findViewById(R.id.submit_link_title);
				final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        	final EditText submitTextTitle = (EditText) findViewById(R.id.submit_text_title);
	        	final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
				if (Constants.TAB_LINK.equals(tabId)) {
					submitLinkTitle.setText(submitTextTitle.getText());
					submitLinkReddit.setText(submitTextReddit.getText());
				} else {
					submitTextTitle.setText(submitLinkTitle.getText());
					submitTextReddit.setText(submitLinkReddit.getText());
				}
			}
		});
		mTabHost.setCurrentTab(0);
		
        // Pull current subreddit and thread info from Intent
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
        	String subreddit = extras.getString(ThreadInfo.SUBREDDIT);
    		final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
        	final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
        	if (Constants.FRONTPAGE_STRING.equals(subreddit)) {
        		submitLinkReddit.setText("reddit.com");
        		submitTextReddit.setText("reddit.com");
        		mSubmitUrl = "http://www.reddit.com/submit";
        	} else {
	        	submitLinkReddit.setText(subreddit);
	        	submitTextReddit.setText(subreddit);
	        	mSubmitUrl = "http://www.reddit.com/r/"+subreddit+"/submit";
        	}
        } else {
        	mSubmitUrl = "http://www.reddit.com/submit";
        }
        
        final Button submitLinkButton = (Button) findViewById(R.id.submit_link_button);
        submitLinkButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (validateLinkForm()) {
	        		final EditText submitLinkTitle = (EditText) findViewById(R.id.submit_link_title);
	        		final EditText submitLinkUrl = (EditText) findViewById(R.id.submit_link_url);
	        		final EditText submitLinkReddit = (EditText) findViewById(R.id.submit_link_reddit);
	        		new SubmitLinkTask(submitLinkTitle.getText(), submitLinkUrl.getText(), submitLinkReddit.getText(),
	        				Constants.SUBMIT_KIND_LINK).execute();
        		}
        	}
        });
        final Button submitTextButton = (Button) findViewById(R.id.submit_text_button);
        submitTextButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (validateTextForm()) {
	        		final EditText submitTextTitle = (EditText) findViewById(R.id.submit_text_title);
	        		final EditText submitTextText = (EditText) findViewById(R.id.submit_text_text);
	        		final EditText submitTextReddit = (EditText) findViewById(R.id.submit_text_reddit);
	        		new SubmitLinkTask(submitTextTitle.getText(), submitTextText.getText(), submitTextReddit.getText(),
	        				Constants.SUBMIT_KIND_SELF).execute();
        		}
        	}
        });
	}

	private class SubmitLinkTask extends AsyncTask<Void, Void, ThreadInfo> {
    	CharSequence _mTitle, _mUrlOrText, _mSubreddit, _mKind;
		String _mUserError = "Error submitting reply. Please try again.";
    	
    	SubmitLinkTask(CharSequence title, CharSequence urlOrText, CharSequence subreddit, CharSequence kind) {
    		_mTitle = title;
    		_mUrlOrText = urlOrText;
    		_mSubreddit = subreddit;
    		_mKind = kind;
    	}
    	
    	@Override
        public ThreadInfo doInBackground(Void... voidz) {
        	ThreadInfo newlyCreatedThread = null;
        	String userError = "Error creating submission. Please try again.";
        	HttpEntity entity = null;
        	
        	String status = "";
        	if (!mSettings.loggedIn) {
        		Common.showErrorToast("You must be logged in to reply.", Toast.LENGTH_LONG, SubmitLinkActivity.this);
        		_mUserError = "Not logged in";
        		return null;
        	}
        	// Update the modhash if necessary
        	if (mModhash == null) {
        		if ((mModhash = Common.doUpdateModhash(mClient)) == null) {
        			// doUpdateModhash should have given an error about credentials
        			Common.doLogout(mSettings, mClient);
        			Log.e(TAG, "Reply failed because doUpdateModhash() failed");
        			return null;
        		}
        	}
        	
        	try {
        		// Construct data
    			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    			nvps.add(new BasicNameValuePair("sr", _mSubreddit.toString()));
    			nvps.add(new BasicNameValuePair("r", _mSubreddit.toString()));
    			nvps.add(new BasicNameValuePair("title", _mTitle.toString()));
    			nvps.add(new BasicNameValuePair("kind", _mKind.toString()));
    			// Put a url or selftext based on the kind of submission
    			if (Constants.SUBMIT_KIND_LINK.equals(_mKind))
    				nvps.add(new BasicNameValuePair("url", _mUrlOrText.toString()));
    			else // if (Constants.SUBMIT_KIND_SELF.equals(_mKind))
    				nvps.add(new BasicNameValuePair("text", _mUrlOrText.toString()));
    			nvps.add(new BasicNameValuePair("uh", mModhash.toString()));
    			// Votehash is currently unused by reddit 
//    				nvps.add(new BasicNameValuePair("vh", "0d4ab0ffd56ad0f66841c15609e9a45aeec6b015"));
    			
    			HttpPost httppost = new HttpPost("http://www.reddit.com/api/submit");
    	        httppost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    	        
    	        Log.d(TAG, nvps.toString());
    	        
                // Perform the HTTP POST request
    	    	HttpResponse response = mClient.execute(httppost);
    	    	status = response.getStatusLine().toString();
            	if (!status.contains("OK"))
            		throw new HttpException(status);
            	
            	entity = response.getEntity();

            	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
            	String line = in.readLine();
            	in.close();
            	if (line == null || Constants.EMPTY_STRING.equals(line)) {
            		throw new HttpException("No content returned from reply POST");
            	}
            	if (line.contains("WRONG_PASSWORD")) {
            		throw new Exception("Wrong password");
            	}
            	if (line.contains("USER_REQUIRED")) {
            		// The modhash probably expired
            		mModhash = null;
            		throw new Exception("User required. Huh?");
            	}
            	if (line.contains("SUBREDDIT_NOEXIST")) {
            		_mUserError = "That subreddit does not exist.";
            		throw new Exception("SUBREDDIT_NOEXIST: " + _mSubreddit);
            	}
            	if (line.contains("SUBREDDIT_NOTALLOWED")) {
            		_mUserError = "You are not allowed to post to that subreddit.";
            		throw new Exception("SUBREDDIT_NOTALLOWED: " + _mSubreddit);
            	}
            	
            	Log.d(TAG, line);

//            	// DEBUG
//            	int c;
//            	boolean done = false;
//            	StringBuilder sb = new StringBuilder();
//            	for (int k = 0; k < line.length(); k += 80) {
//            		for (int i = 0; i < 80; i++) {
//            			if (k + i >= line.length()) {
//            				done = true;
//            				break;
//            			}
//            			c = line.charAt(k + i);
//            			sb.append((char) c);
//            		}
//            		Log.d(TAG, "doReply response content: " + sb.toString());
//            		sb = new StringBuilder();
//            		if (done)
//            			break;
//            	}
//    	        	

            	String newId, newSubreddit;
            	Matcher idMatcher = Constants.NEW_THREAD_PATTERN.matcher(line);
            	if (idMatcher.find()) {
            		newSubreddit = idMatcher.group(1);
            		newId = idMatcher.group(2);
            	} else {
            		if (line.contains("RATELIMIT")) {
                		// Try to find the # of minutes using regex
                    	Matcher rateMatcher = Constants.RATELIMIT_RETRY_PATTERN.matcher(line);
                    	if (rateMatcher.find())
                    		userError = rateMatcher.group(1);
                    	else
                    		userError = "you are trying to submit too fast. try again in a few minutes.";
                		throw new Exception(userError);
                	}
            		if (line.contains("BAD_CAPTCHA")) {
            			userError = "Bad CAPTCHA. Try again.";
            			new DownloadCaptchaTask().execute();
            		}
                	throw new Exception("No id returned by reply POST.");
            	}
            	
            	entity.consumeContent();
            	
            	// Getting here means success. Create a new ThreadInfo.
            	newlyCreatedThread = new ThreadInfo();
            	// We only need to fill in a few fields.
            	newlyCreatedThread.put(ThreadInfo.ID, newId);
            	newlyCreatedThread.put(ThreadInfo.SUBREDDIT, newSubreddit);
            	newlyCreatedThread.put(ThreadInfo.TITLE, _mTitle.toString());
            	
            	return newlyCreatedThread;
            	
        	} catch (Exception e) {
        		if (entity != null) {
        			try {
        				entity.consumeContent();
        			} catch (IOException e2) {
        				Log.e(TAG, e.getMessage());
        			}
        		}
                Log.e(TAG, e.getMessage());
        	}
        	return null;
        }
    	
    	@Override
    	public void onPreExecute() {
    		showDialog(Constants.DIALOG_SUBMITTING);
    	}
    	
    	
    	@Override
    	public void onPostExecute(ThreadInfo newlyCreatedThread) {
    		dismissDialog(Constants.DIALOG_SUBMITTING);
    		if (newlyCreatedThread == null) {
    			Common.showErrorToast(_mUserError, Toast.LENGTH_LONG, SubmitLinkActivity.this);
    		} else {
        		// Success. Return the subreddit and thread id
    			Intent i = new Intent();
    			i.putExtra(ThreadInfo.SUBREDDIT, newlyCreatedThread.getSubreddit());
    			i.putExtra(ThreadInfo.ID, newlyCreatedThread.getId());
    			setResult(Activity.RESULT_OK, i);
    			finish();
    		}
    	}
    }
	
	private class CheckCaptchaRequiredTask extends AsyncTask<Void, Void, Boolean> {
		@Override
		public Boolean doInBackground(Void... voidz) {
			HttpEntity entity = null;
			try {
				HttpGet request = new HttpGet("http://www.reddit.com" + mCaptchaUrl);
				HttpResponse response = mClient.execute(request);
	    	
            	BufferedReader in = new BufferedReader(new InputStreamReader(entity.getContent()));
            	String line = in.readLine();
            	in.close();

            	Matcher idenMatcher = Constants.CAPTCHA_IDEN_PATTERN.matcher(line);
            	Matcher urlMatcher = Constants.CAPTCHA_IMAGE_PATTERN.matcher(line);
            	if (idenMatcher.find() && urlMatcher.find()) {
            		mCaptchaIden = idenMatcher.group(1);
            		mCaptchaUrl = urlMatcher.group(2);
            	} else {
            		entity.consumeContent();
            		return false;
            	}
			} catch (Exception e) {
				if (entity != null) {
					try {
						entity.consumeContent();
					} catch (Exception e2) {
						Log.e(TAG, e.getMessage());
					}
				}
				Log.e(TAG, "Error accessing "+mSubmitUrl+" to check for CAPTCHA");
			}
			return true;
		}
		
		@Override
		public void onPostExecute(Boolean required) {
			final TextView linkCaptchaLabel = (TextView) findViewById(R.id.submit_link_captcha_label);
			final ImageView linkCaptchaImage = (ImageView) findViewById(R.id.submit_link_captcha_image);
			final EditText linkCaptchaEdit = (EditText) findViewById(R.id.submit_link_captcha);
			final TextView textCaptchaLabel = (TextView) findViewById(R.id.submit_text_captcha_label);
			final ImageView textCaptchaImage = (ImageView) findViewById(R.id.submit_text_captcha_image);
			final EditText textCaptchaEdit = (EditText) findViewById(R.id.submit_text_captcha);
			if (required) {
				linkCaptchaLabel.setVisibility(View.VISIBLE);
				linkCaptchaImage.setVisibility(View.VISIBLE);
				linkCaptchaEdit.setVisibility(View.VISIBLE);
				textCaptchaLabel.setVisibility(View.VISIBLE);
				textCaptchaImage.setVisibility(View.VISIBLE);
				textCaptchaEdit.setVisibility(View.VISIBLE);
				new DownloadCaptchaTask().execute();
			} else {
				linkCaptchaLabel.setVisibility(View.GONE);
				linkCaptchaImage.setVisibility(View.GONE);
				linkCaptchaEdit.setVisibility(View.GONE);
				textCaptchaLabel.setVisibility(View.GONE);
				textCaptchaImage.setVisibility(View.GONE);
				textCaptchaEdit.setVisibility(View.GONE);
			}
		}
	}
	
	private class DownloadCaptchaTask extends AsyncTask<Void, Void, Drawable> {
		@Override
		public Drawable doInBackground(Void... voidz) {
			try {
				HttpGet request = new HttpGet("http://www.reddit.com/" + mCaptchaUrl);
				HttpResponse response = mClient.execute(request);
	    	
				InputStream in = response.getEntity().getContent();
				
				return Drawable.createFromStream(in, "captcha");
			
			} catch (Exception e) {
				Common.showErrorToast("Error downloading captcha.", Toast.LENGTH_LONG, SubmitLinkActivity.this);
			}
			
			return null;
		}
		
		@Override
		public void onPostExecute(Drawable captcha) {
			final ImageView linkCaptchaView = (ImageView) findViewById(R.id.submit_link_captcha_image);
			final ImageView textCaptchaView = (ImageView) findViewById(R.id.submit_text_captcha_image);
			linkCaptchaView.setVisibility(View.VISIBLE);
			textCaptchaView.setImageDrawable(captcha);
		}
	}
    
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		AutoResetProgressDialog pdialog;
		switch (id) {
		case Constants.DIALOG_SUBMITTING:
    		pdialog = new AutoResetProgressDialog(this);
    		pdialog.setMessage("Submitting...");
    		pdialog.setIndeterminate(true);
    		pdialog.setCancelable(false);
    		dialog = pdialog;
    		break;
		default:
    		break;
		}
		return dialog;
	}
	
	private boolean validateLinkForm() {
		final EditText titleText = (EditText) findViewById(R.id.submit_link_title);
		final EditText urlText = (EditText) findViewById(R.id.submit_link_url);
		final EditText redditText = (EditText) findViewById(R.id.submit_link_reddit);
		if (Constants.EMPTY_STRING.equals(titleText.getText())) {
			Common.showErrorToast("Please provide a title.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Constants.EMPTY_STRING.equals(urlText.getText())) {
			Common.showErrorToast("Please provide a URL.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Constants.EMPTY_STRING.equals(redditText.getText())) {
			Common.showErrorToast("Please provide a subreddit.", Toast.LENGTH_LONG, this);
			return false;
		}
		return true;
	}
	private boolean validateTextForm() {
		final EditText titleText = (EditText) findViewById(R.id.submit_text_title);
		final EditText redditText = (EditText) findViewById(R.id.submit_text_reddit);
		if (Constants.EMPTY_STRING.equals(titleText.getText())) {
			Common.showErrorToast("Please provide a title.", Toast.LENGTH_LONG, this);
			return false;
		}
		if (Constants.EMPTY_STRING.equals(redditText.getText())) {
			Common.showErrorToast("Please provide a subreddit.", Toast.LENGTH_LONG, this);
			return false;
		}
		return true;
	}
}
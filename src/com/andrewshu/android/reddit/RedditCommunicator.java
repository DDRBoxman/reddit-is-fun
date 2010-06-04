package com.andrewshu.android.reddit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.util.Log;

public class RedditCommunicator {

	private static final DefaultHttpClient mHttpClient = new DefaultHttpClient();
	private static final String mSiteURL = "http://www.reddit.com/";
	
	public void postRequest(String path, List<NameValuePair> parameters ) {
		HttpPost httppost = new HttpPost(mSiteURL + path);
		try {
			httppost.setEntity(new UrlEncodedFormEntity(parameters, HTTP.UTF_8));
			doRequest(httppost);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void getRequest(String path) {
		HttpGet httpget = new HttpGet(mSiteURL + path);
		doRequest(httpget);
	}
	
	private InputStream doRequest(HttpRequestBase request) {
		try {
		     // Perform the HTTP request
	    	HttpResponse response;
			response = mHttpClient.execute(request);
			HttpEntity entity = response.getEntity();
        	return entity.getContent();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
	
	public void vote(String id, String voteDirection, String subreddit) {
		// Construct data
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("id", id));
		nvps.add(new BasicNameValuePair("dir", voteDirection));
		nvps.add(new BasicNameValuePair("r", subreddit));
		nvps.add(new BasicNameValuePair("uh", AuthenticatedUser.mModhash));
		postRequest("api/vote",nvps);
	}
	
	public void login() {
		
	}
	
	public void loadSubreddit(String subreddit) {
		getRequest(subreddit + ".json");
	}
}

package com.andrewshu.android.reddit;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Window;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class RedditIsFun extends Activity {

	ListView mThreadList;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        //remove automatic window title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.threads_list_content);
        
        mThreadList = (ListView) findViewById(R.id.threads_list_content_list);
        setCursorAdapter();
	}
	
	private void setCursorAdapter() {
		Cursor c = mDbHelper.fetchAllNotes();
        startManagingCursor(c);
        
        String[] from = new String[] { NotesDbAdapter.KEY_TITLE };
        int[] to = new int[] { R.id.title , R.id.nsfw, R.id.numComments, R.id.subreddit, R.id.submissionTime, R.id.submitter};
        
        SimpleCursorAdapter notes =
            new SimpleCursorAdapter(this, R.layout.threads_list_item, c, from, to);
        mThreadList.setAdapter(notes);
	}
	
}

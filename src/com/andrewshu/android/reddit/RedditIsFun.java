package com.andrewshu.android.reddit;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

public class RedditIsFun extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        //remove automatic window title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.threads_list_content);
        
        
	}
	
}

package com.andrewshu.android.reddit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class RedditIsFunDashboard extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //remove automatic window title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.dashboard);
	}

	public void itemClicked(View view) {
		switch (view.getId()) {
			case R.id.DashboardButtonFrontPage:
				startActivity(new Intent(this, RedditIsFun.class));
				break;
				
			case R.id.DashboardButtonSubreddit:
				startActivity(new Intent(this, PickSubredditActivity.class));
				break;
			
			case R.id.DashboardButtonInbox:
				startActivity(new Intent(this, InboxActivity.class));
				break;
		}
	}
}

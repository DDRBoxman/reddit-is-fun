package com.andrewshu.android.reddit;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.IBinder;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class MessageWidgetProvider extends AppWidgetProvider{
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
	        int[] appWidgetIds) {
	        // To prevent any ANR timeouts, we perform the update in a service
	        context.startService(new Intent(context, UpdateService.class));
	}
	
	
	public static class UpdateService extends Service {
        @Override
        public void onStart(Intent intent, int startId) {
            // Build the widget update for today
            RemoteViews updateViews = buildUpdate(this);

            // Push update for this widget to the home screen
            ComponentName thisWidget = new ComponentName(this, MessageWidgetProvider.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }

        public RemoteViews buildUpdate(Context context) {
            // Pick out month names from resources
            Resources res = context.getResources();

            //get unread messages number
            DefaultHttpClient mClient = Common.getGzipHttpClient();
            HttpGet request = new HttpGet("http://www.reddit.com/message/inbox/.json?mark=false");
        	HttpResponse response;
        	
        	Integer count = 0;
        	
			try {
				response = mClient.execute(request);
				HttpEntity entity = response.getEntity();
	        	InputStream in = entity.getContent();
	        	try {
	        		count = Common.processInboxJSON(in);
	        	} catch (Exception e) {
	        		
	        	}
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	

        	RemoteViews updateViews;
        	
        	//count += 9;
        	
            if (count != null && count > 0) {
                // Build an update that holds the updated widget contents
            	updateViews = new RemoteViews(context.getPackageName(), R.layout.message_widget_layout);
            	updateViews.setViewVisibility(R.id.UnreadMessagesImageView, 0);
            	
            	//draw the count onto the circle!
            	Bitmap temp = BitmapFactory.decodeResource(getResources(), R.drawable.unread_sphere);
            	//make bitmap mutable
            	Bitmap unreadSphere = temp.copy(temp.getConfig(), true);
            	
            	TextPaint paint = new TextPaint();
            	paint.setColor(Color.WHITE);

            	//determine text size and pos
            	if (count < 10) {
            		paint.setTextSize(25);
            	} else if (count < 100) {
            		paint.setTextSize(22);
            	} else {
            		paint.setTextSize(16);
            	}
            	
            	float textX = (float) ((unreadSphere.getWidth() - paint.measureText("" + count)) / 2.0);
            	float textY = (float) (paint.getTextSize() /2  + (unreadSphere.getHeight() /2));
            	
            	Canvas unreadCanvas = new Canvas(unreadSphere);
            	Log.i("" ,"" + unreadSphere.getHeight());
            	unreadCanvas.drawText("" + count, textX, textY, paint);            	
            	
            	updateViews.setImageViewBitmap(R.id.UnreadMessagesImageView, unreadSphere);
            	
                Intent defineIntent = new Intent(getBaseContext(), InboxActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(context,
                        0 /* no requestCode */, defineIntent, 0 /* no flags */);
                updateViews.setOnClickPendingIntent(R.id.widget, pendingIntent);
    
            } else {
                Log.i("reddit :D","no count :(");
            	updateViews = new RemoteViews(context.getPackageName(), R.layout.message_widget_layout);
            	updateViews.setViewVisibility(R.id.UnreadMessagesImageView, View.GONE);
            	Intent defineIntent = new Intent(getBaseContext(), RedditIsFun.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(context,
                        0 /* no requestCode */, defineIntent, 0 /* no flags */);
                updateViews.setOnClickPendingIntent(R.id.widget, pendingIntent);
            }
            return updateViews;
        }

        @Override
        public IBinder onBind(Intent intent) {
            // We don't need to bind to this service
            return null;
        }
    }
}

package com.example.fielddatacollector;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

/**
 * Application class for Field Data Collector.
 * Handles app-wide initialization like notification channels.
 */
public class FieldDataApp extends Application {
    
    public static final String CHANNEL_BUBBLE = "bubble_channel";
    public static final String CHANNEL_CAPTURE = "capture_channel";
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }
    
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Bubble Service Channel
            NotificationChannel bubbleChannel = new NotificationChannel(
                CHANNEL_BUBBLE,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            );
            bubbleChannel.setDescription(getString(R.string.notification_channel_desc));
            bubbleChannel.setShowBadge(false);
            
            // Screen Capture Channel
            NotificationChannel captureChannel = new NotificationChannel(
                CHANNEL_CAPTURE,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            );
            captureChannel.setDescription("Notification for screen capture service");
            captureChannel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(bubbleChannel);
                manager.createNotificationChannel(captureChannel);
            }
        }
    }
}

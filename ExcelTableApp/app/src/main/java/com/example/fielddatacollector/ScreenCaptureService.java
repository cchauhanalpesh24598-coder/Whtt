package com.example.fielddatacollector;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;

/**
 * Service that handles screen capture for OCR processing.
 * Uses MediaProjection API to capture the screen.
 */
public class ScreenCaptureService extends Service {
    
    private static final String TAG = "ScreenCaptureService";
    private static final int NOTIFICATION_ID = 2;
    
    public static final String ACTION_START = "com.example.fielddatacollector.START_CAPTURE";
    public static final String ACTION_STOP = "com.example.fielddatacollector.STOP_CAPTURE";
    public static final String ACTION_CAPTURE = "com.example.fielddatacollector.DO_CAPTURE";
    
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "data";
    
    // Broadcast for capture result
    public static final String ACTION_CAPTURE_RESULT = "com.example.fielddatacollector.CAPTURE_RESULT";
    public static final String EXTRA_BITMAP = "bitmap";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_ERROR = "error";
    
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    private static ScreenCaptureService instance;
    private static boolean isRunning = false;
    
    // Callback interface for capture results
    public interface CaptureCallback {
        void onCaptureComplete(Bitmap bitmap);
        void onCaptureFailed(String error);
    }
    
    private static CaptureCallback pendingCallback;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        instance = this;
        handler = new Handler(Looper.getMainLooper());
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        // Get screen dimensions
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        
        if (ACTION_START.equals(action)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            
            if (resultCode == Activity.RESULT_OK && data != null) {
                startForegroundNotification();
                startProjection(resultCode, data);
            }
        } else if (ACTION_CAPTURE.equals(action)) {
            captureScreen();
        } else if (ACTION_STOP.equals(action)) {
            stopProjection();
            stopSelf();
        }
        
        return START_NOT_STICKY;
    }
    
    private void startForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, FieldDataApp.CHANNEL_CAPTURE)
            .setContentTitle(getString(R.string.notification_capture_title))
            .setContentText(getString(R.string.notification_capture_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
    
    private void startProjection(int resultCode, Intent data) {
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null");
            broadcastError("Could not start screen capture");
            return;
        }
        
        // Register callback for projection stop
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.d(TAG, "MediaProjection stopped");
                cleanupVirtualDisplay();
            }
        }, handler);
        
        createVirtualDisplay();
        isRunning = true;
        
        Log.d(TAG, "Screen capture started");
    }
    
    private void createVirtualDisplay() {
        // Create ImageReader
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        );
        
        // Create VirtualDisplay
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),
            null, handler
        );
    }
    
    private void captureScreen() {
        if (imageReader == null) {
            Log.e(TAG, "ImageReader is null");
            broadcastError("Screen capture not initialized");
            return;
        }
        
        // Small delay to ensure image is ready
        handler.postDelayed(() -> {
            try {
                Image image = imageReader.acquireLatestImage();
                
                if (image == null) {
                    Log.e(TAG, "Captured image is null");
                    broadcastError("Could not capture screen");
                    return;
                }
                
                Bitmap bitmap = imageToBitmap(image);
                image.close();
                
                if (bitmap != null) {
                    Log.d(TAG, "Screen captured successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    
                    // Process with OCR
                    processWithOCR(bitmap);
                } else {
                    broadcastError("Could not create bitmap from capture");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error capturing screen", e);
                broadcastError("Capture error: " + e.getMessage());
            }
        }, 100);
    }
    
    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        
        // Create bitmap
        Bitmap bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);
        
        // Crop to actual screen size if there's padding
        if (rowPadding > 0) {
            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
            bitmap.recycle();
            return croppedBitmap;
        }
        
        return bitmap;
    }
    
    private void processWithOCR(Bitmap bitmap) {
        OCRProcessor.processImage(bitmap, new OCRProcessor.OCRCallback() {
            @Override
            public void onSuccess(String extractedText, String latLong) {
                Log.d(TAG, "OCR success: " + latLong);
                
                // Update data manager
                if (latLong != null && !latLong.isEmpty()) {
                    DataManager.getInstance(ScreenCaptureService.this)
                        .updateCurrentRowFromOCR(latLong);
                }
                
                broadcastSuccess(latLong);
                
                // Callback if set
                if (pendingCallback != null) {
                    pendingCallback.onCaptureComplete(bitmap);
                    pendingCallback = null;
                }
            }
            
            @Override
            public void onFailure(String error) {
                Log.e(TAG, "OCR failed: " + error);
                broadcastError(error);
                
                // Callback if set
                if (pendingCallback != null) {
                    pendingCallback.onCaptureFailed(error);
                    pendingCallback = null;
                }
            }
        });
    }
    
    private void broadcastSuccess(String latLong) {
        Intent intent = new Intent(ACTION_CAPTURE_RESULT);
        intent.putExtra(EXTRA_SUCCESS, true);
        intent.putExtra("lat_long", latLong != null ? latLong : "");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    private void broadcastError(String error) {
        Intent intent = new Intent(ACTION_CAPTURE_RESULT);
        intent.putExtra(EXTRA_SUCCESS, false);
        intent.putExtra(EXTRA_ERROR, error);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    private void stopProjection() {
        isRunning = false;
        cleanupVirtualDisplay();
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
    
    private void cleanupVirtualDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProjection();
        instance = null;
        Log.d(TAG, "Service destroyed");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    // Static methods for external access
    public static boolean isRunning() {
        return isRunning;
    }
    
    public static void requestCapture(Context context) {
        if (instance != null && isRunning) {
            Intent intent = new Intent(context, ScreenCaptureService.class);
            intent.setAction(ACTION_CAPTURE);
            context.startService(intent);
        }
    }
    
    public static void setCaptureCallback(CaptureCallback callback) {
        pendingCallback = callback;
    }
}

package com.example.fielddatacollector;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.example.fielddatacollector.model.FieldData;

/**
 * Floating Bubble Service that shows a draggable bubble overlay.
 * Features:
 * - Circular bubble with row number
 * - Progress ring (empty/half/full)
 * - Tap to show popup with data preview and actions
 * - Long press to close
 * - Draggable anywhere on screen
 */
public class FloatingBubbleService extends Service implements DataManager.DataChangeListener {
    
    private static final String TAG = "FloatingBubbleService";
    private static final int NOTIFICATION_ID = 1;
    
    public static final String ACTION_SHOW_BUBBLE = "com.example.fielddatacollector.SHOW_BUBBLE";
    public static final String ACTION_HIDE_BUBBLE = "com.example.fielddatacollector.HIDE_BUBBLE";
    
    private WindowManager windowManager;
    private View bubbleView;
    private View popupView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams popupParams;
    
    private TextView rowNumberText;
    private ImageView progressRing;
    
    // Popup views
    private TextView popupRowNumber;
    private TextView popupBank;
    private TextView popupApplicant;
    private TextView popupReason;
    private TextView popupLatLong;
    private Button btnScan;
    private Button btnNext;
    private Button btnViewTable;
    
    private DataManager dataManager;
    private Handler handler;
    private Vibrator vibrator;
    
    private boolean isPopupShowing = false;
    private boolean isDragging = false;
    
    // Touch handling
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    private long touchStartTime;
    private static final long LONG_PRESS_DURATION = 800;
    private static final int CLICK_THRESHOLD = 10;
    
    private static boolean isRunning = false;
    
    // Broadcast receiver for clipboard data and capture results
    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (ClipboardAccessibilityService.ACTION_CLIPBOARD_DATA.equals(action)) {
                // Clipboard data received
                String bank = intent.getStringExtra(ClipboardAccessibilityService.EXTRA_BANK_NAME);
                String applicant = intent.getStringExtra(ClipboardAccessibilityService.EXTRA_APPLICANT_NAME);
                String reason = intent.getStringExtra(ClipboardAccessibilityService.EXTRA_REASON);
                
                handler.post(() -> {
                    updatePopupData();
                    updateProgressRing();
                    vibrate(50);
                });
                
            } else if (ScreenCaptureService.ACTION_CAPTURE_RESULT.equals(action)) {
                // Screen capture result
                boolean success = intent.getBooleanExtra(ScreenCaptureService.EXTRA_SUCCESS, false);
                
                handler.post(() -> {
                    if (success) {
                        String latLong = intent.getStringExtra("lat_long");
                        Toast.makeText(context, getString(R.string.toast_ocr_success, latLong), Toast.LENGTH_SHORT).show();
                        updatePopupData();
                        updateProgressRing();
                        vibrate(100);
                    } else {
                        String error = intent.getStringExtra(ScreenCaptureService.EXTRA_ERROR);
                        Toast.makeText(context, getString(R.string.toast_ocr_failed) + ": " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        handler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        dataManager = DataManager.getInstance(this);
        dataManager.addListener(this);
        
        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ClipboardAccessibilityService.ACTION_CLIPBOARD_DATA);
        filter.addAction(ScreenCaptureService.ACTION_CAPTURE_RESULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dataReceiver, filter);
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            
            if (ACTION_SHOW_BUBBLE.equals(action)) {
                showBubble();
            } else if (ACTION_HIDE_BUBBLE.equals(action)) {
                hideBubble();
            } else {
                showBubble();
            }
        } else {
            showBubble();
        }
        
        return START_STICKY;
    }
    
    private void showBubble() {
        if (bubbleView != null) {
            return; // Already showing
        }
        
        startForegroundNotification();
        createBubbleView();
        createPopupView();
        
        isRunning = true;
        Log.d(TAG, "Bubble shown");
    }
    
    private void hideBubble() {
        hidePopup();
        
        if (bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing bubble", e);
            }
            bubbleView = null;
        }
        
        isRunning = false;
        stopForeground(true);
        stopSelf();
        
        Log.d(TAG, "Bubble hidden");
    }
    
    private void startForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, FieldDataApp.CHANNEL_BUBBLE)
            .setContentTitle(getString(R.string.notification_bubble_title))
            .setContentText(getString(R.string.notification_bubble_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
    
    private void createBubbleView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        bubbleView = inflater.inflate(R.layout.floating_bubble, null);
        
        rowNumberText = bubbleView.findViewById(R.id.row_number);
        progressRing = bubbleView.findViewById(R.id.progress_ring);
        
        // Update initial state
        updateRowNumber();
        updateProgressRing();
        
        // Set up layout params
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        
        bubbleParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 0;
        bubbleParams.y = 200;
        
        // Set up touch listener
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleBubbleTouch(event);
            }
        });
        
        windowManager.addView(bubbleView, bubbleParams);
    }
    
    private void createPopupView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        popupView = inflater.inflate(R.layout.bubble_popup, null);
        
        popupRowNumber = popupView.findViewById(R.id.popup_row_number);
        popupBank = popupView.findViewById(R.id.popup_bank);
        popupApplicant = popupView.findViewById(R.id.popup_applicant);
        popupReason = popupView.findViewById(R.id.popup_reason);
        popupLatLong = popupView.findViewById(R.id.popup_latlong);
        btnScan = popupView.findViewById(R.id.btn_scan);
        btnNext = popupView.findViewById(R.id.btn_next);
        btnViewTable = popupView.findViewById(R.id.btn_view_table);
        
        // Set up button listeners
        btnScan.setOnClickListener(v -> {
            hidePopup();
            requestScreenCapture();
        });
        
        btnNext.setOnClickListener(v -> {
            dataManager.moveToNextRow();
            updateRowNumber();
            updateProgressRing();
            updatePopupData();
            vibrate(30);
        });
        
        btnViewTable.setOnClickListener(v -> {
            hidePopup();
            openTableActivity();
        });
        
        // Close popup when clicking outside
        popupView.setOnClickListener(v -> hidePopup());
        popupView.findViewById(R.id.popup_card).setOnClickListener(v -> {
            // Don't close when clicking card itself
        });
        
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        
        popupParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        
        popupParams.gravity = Gravity.TOP | Gravity.START;
    }
    
    private boolean handleBubbleTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = bubbleParams.x;
                initialY = bubbleParams.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                touchStartTime = System.currentTimeMillis();
                isDragging = false;
                
                // Schedule long press check
                handler.postDelayed(longPressRunnable, LONG_PRESS_DURATION);
                return true;
                
            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - initialTouchX;
                float deltaY = event.getRawY() - initialTouchY;
                
                if (Math.abs(deltaX) > CLICK_THRESHOLD || Math.abs(deltaY) > CLICK_THRESHOLD) {
                    isDragging = true;
                    handler.removeCallbacks(longPressRunnable);
                    
                    bubbleParams.x = initialX + (int) deltaX;
                    bubbleParams.y = initialY + (int) deltaY;
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                }
                return true;
                
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                
                long pressDuration = System.currentTimeMillis() - touchStartTime;
                
                if (!isDragging && pressDuration < LONG_PRESS_DURATION) {
                    // Single tap - toggle popup
                    if (isPopupShowing) {
                        hidePopup();
                    } else {
                        showPopup();
                    }
                }
                return true;
        }
        return false;
    }
    
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isDragging) {
                // Long press detected - close bubble
                vibrate(100);
                hideBubble();
            }
        }
    };
    
    private void showPopup() {
        if (isPopupShowing || popupView == null) return;
        
        // Update popup data
        updatePopupData();
        
        // Position popup near bubble
        popupParams.x = bubbleParams.x + 70;
        popupParams.y = bubbleParams.y;
        
        try {
            windowManager.addView(popupView, popupParams);
            isPopupShowing = true;
            vibrate(20);
        } catch (Exception e) {
            Log.e(TAG, "Error showing popup", e);
        }
    }
    
    private void hidePopup() {
        if (!isPopupShowing || popupView == null) return;
        
        try {
            windowManager.removeView(popupView);
            isPopupShowing = false;
        } catch (Exception e) {
            Log.e(TAG, "Error hiding popup", e);
        }
    }
    
    private void updateRowNumber() {
        if (rowNumberText != null) {
            rowNumberText.setText(String.valueOf(dataManager.getCurrentRowNumber()));
        }
    }
    
    private void updateProgressRing() {
        if (progressRing == null) return;
        
        int state = dataManager.getCurrentRowProgressState();
        
        switch (state) {
            case 0: // Empty
                progressRing.setImageResource(R.drawable.progress_ring_empty);
                break;
            case 1: // Half (message only)
                progressRing.setImageResource(R.drawable.progress_ring_half);
                break;
            case 2: // Full (message + image)
                progressRing.setImageResource(R.drawable.progress_ring_full);
                break;
        }
    }
    
    private void updatePopupData() {
        if (popupRowNumber == null) return;
        
        FieldData data = dataManager.getCurrentRow();
        
        popupRowNumber.setText("Row " + data.getSrNo());
        popupBank.setText(data.getBankName().isEmpty() ? "-" : data.getBankName());
        popupApplicant.setText(data.getApplicantName().isEmpty() ? "-" : data.getApplicantName());
        popupReason.setText(data.getReasonForCnv().isEmpty() ? "-" : data.getReasonForCnv());
        popupLatLong.setText(data.getLatLongTo().isEmpty() ? "-" : data.getLatLongTo());
    }
    
    private void requestScreenCapture() {
        // Send broadcast to MainActivity to request screen capture permission
        Intent intent = new Intent("com.example.fielddatacollector.REQUEST_CAPTURE");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        
        Toast.makeText(this, "Open the image and grant capture permission", Toast.LENGTH_SHORT).show();
    }
    
    private void openTableActivity() {
        Intent intent = new Intent(this, TableActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    
    private void vibrate(int duration) {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }
    
    // DataChangeListener callbacks
    @Override
    public void onDataChanged(java.util.List<FieldData> data) {
        handler.post(() -> {
            updatePopupData();
        });
    }
    
    @Override
    public void onCurrentRowChanged(int rowIndex, FieldData data) {
        handler.post(() -> {
            updateRowNumber();
            updateProgressRing();
            updatePopupData();
        });
    }
    
    @Override
    public void onRowCompleted(int rowIndex) {
        handler.post(() -> {
            Toast.makeText(this, getString(R.string.toast_row_complete, rowIndex + 1), Toast.LENGTH_SHORT).show();
            vibrate(150);
        });
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        hidePopup();
        
        if (bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing bubble on destroy", e);
            }
        }
        
        try {
            unregisterReceiver(dataReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        
        dataManager.removeListener(this);
        isRunning = false;
        
        Log.d(TAG, "Service destroyed");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    public static boolean isRunning() {
        return isRunning;
    }
}

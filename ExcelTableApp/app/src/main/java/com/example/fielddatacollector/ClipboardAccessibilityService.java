package com.example.fielddatacollector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * Accessibility Service that monitors clipboard for WhatsApp message data.
 * When user copies text, this service detects it and parses the message
 * to extract Bank Name, Applicant Name, and Reason.
 */
public class ClipboardAccessibilityService extends AccessibilityService {
    
    private static final String TAG = "ClipboardService";
    
    // Broadcast actions
    public static final String ACTION_CLIPBOARD_DATA = "com.example.fielddatacollector.CLIPBOARD_DATA";
    public static final String EXTRA_BANK_NAME = "bank_name";
    public static final String EXTRA_APPLICANT_NAME = "applicant_name";
    public static final String EXTRA_REASON = "reason";
    public static final String EXTRA_RAW_TEXT = "raw_text";
    
    // Status broadcast
    public static final String ACTION_SERVICE_STATUS = "com.example.fielddatacollector.SERVICE_STATUS";
    public static final String EXTRA_IS_ACTIVE = "is_active";
    
    private ClipboardManager clipboardManager;
    private String lastClipboardText = "";
    private Handler handler;
    private DataManager dataManager;
    
    private static boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        handler = new Handler(Looper.getMainLooper());
    }
    
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Service connected");
        
        // Configure the accessibility service
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        
        // Initialize clipboard manager
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        
        // Initialize data manager
        dataManager = DataManager.getInstance(this);
        
        // Set up clipboard listener
        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener);
        }
        
        isRunning = true;
        broadcastServiceStatus(true);
        
        Log.d(TAG, "Clipboard listener registered");
    }
    
    private final ClipboardManager.OnPrimaryClipChangedListener clipboardListener = 
        new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                handler.post(() -> processClipboard());
            }
        };
    
    /**
     * Process clipboard content
     */
    private void processClipboard() {
        if (clipboardManager == null) return;
        
        try {
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() == 0) return;
            
            ClipData.Item item = clipData.getItemAt(0);
            CharSequence text = item.getText();
            
            if (text == null || text.length() == 0) return;
            
            String clipboardText = text.toString();
            
            // Check if it's new text (not already processed)
            if (clipboardText.equals(lastClipboardText)) {
                return;
            }
            
            lastClipboardText = clipboardText;
            
            // Check if it looks like a field report message
            if (!MessageParser.isFieldReportMessage(clipboardText)) {
                Log.d(TAG, "Clipboard text doesn't look like field report, ignoring");
                return;
            }
            
            Log.d(TAG, "Processing clipboard text: " + clipboardText.substring(0, Math.min(50, clipboardText.length())));
            
            // Parse the message
            MessageParser.ParseResult result = MessageParser.parse(clipboardText);
            
            if (result.isValid) {
                Log.d(TAG, "Parsed result: " + result);
                
                // Update data manager
                dataManager.updateCurrentRowFromMessage(
                    result.bankName,
                    result.applicantName,
                    result.reason
                );
                
                // Broadcast the extracted data
                broadcastExtractedData(result, clipboardText);
                
                // Show toast
                showToast("Data extracted: " + result.applicantName);
            } else {
                Log.d(TAG, "Could not parse message");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing clipboard", e);
        }
    }
    
    /**
     * Broadcast extracted data to other components (like FloatingBubbleService)
     */
    private void broadcastExtractedData(MessageParser.ParseResult result, String rawText) {
        Intent intent = new Intent(ACTION_CLIPBOARD_DATA);
        intent.putExtra(EXTRA_BANK_NAME, result.bankName);
        intent.putExtra(EXTRA_APPLICANT_NAME, result.applicantName);
        intent.putExtra(EXTRA_REASON, result.reason);
        intent.putExtra(EXTRA_RAW_TEXT, rawText);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    /**
     * Broadcast service status
     */
    private void broadcastServiceStatus(boolean isActive) {
        Intent intent = new Intent(ACTION_SERVICE_STATUS);
        intent.putExtra(EXTRA_IS_ACTIVE, isActive);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    
    /**
     * Show toast message
     */
    private void showToast(final String message) {
        handler.post(() -> Toast.makeText(
            ClipboardAccessibilityService.this, 
            message, 
            Toast.LENGTH_SHORT
        ).show());
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't need to handle accessibility events directly
        // The clipboard listener handles our needs
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        // Remove clipboard listener
        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }
        
        isRunning = false;
        broadcastServiceStatus(false);
    }
    
    /**
     * Check if the service is currently running
     */
    public static boolean isServiceRunning() {
        return isRunning;
    }
    
    /**
     * Static method to check if accessibility service is enabled
     */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        try {
            String enabledServices = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            
            if (enabledServices == null) return false;
            
            String serviceName = context.getPackageName() + "/" + 
                ClipboardAccessibilityService.class.getCanonicalName();
            
            return enabledServices.contains(serviceName);
        } catch (Exception e) {
            Log.e(TAG, "Error checking accessibility service status", e);
            return false;
        }
    }
}

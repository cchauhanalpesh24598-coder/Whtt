package com.example.fielddatacollector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.fielddatacollector.model.FieldData;

import java.io.File;
import java.util.List;

/**
 * Main Activity - Control panel for the Field Data Collector app.
 * Manages permissions, bubble service, and provides quick actions.
 */
public class MainActivity extends AppCompatActivity implements DataManager.DataChangeListener {
    
    private static final int REQUEST_OVERLAY = 1001;
    
    // UI Elements
    private View statusBubbleIndicator;
    private View statusAccessibilityIndicator;
    private View statusDataIndicator;
    private TextView statusBubbleText;
    private TextView statusAccessibilityText;
    private TextView statusDataText;
    private ImageView iconOverlay;
    private ImageView iconAccessibility;
    private Button btnToggleBubble;
    
    private DataManager dataManager;
    private MediaProjectionManager projectionManager;
    
    // Screen capture permission launcher
    private final ActivityResultLauncher<Intent> screenCaptureLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                // Start screen capture service
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                serviceIntent.setAction(ScreenCaptureService.ACTION_START);
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.getResultCode());
                serviceIntent.putExtra(ScreenCaptureService.EXTRA_DATA, result.getData());
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                Toast.makeText(this, "Screen capture ready", Toast.LENGTH_SHORT).show();
            }
        }
    );
    
    // Broadcast receiver for capture requests from bubble
    private final BroadcastReceiver captureRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.fielddatacollector.REQUEST_CAPTURE".equals(intent.getAction())) {
                requestScreenCapture();
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        dataManager = DataManager.getInstance(this);
        dataManager.addListener(this);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        initViews();
        setupListeners();
        
        // Register capture request receiver
        IntentFilter filter = new IntentFilter("com.example.fielddatacollector.REQUEST_CAPTURE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureRequestReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(captureRequestReceiver, filter);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
    
    private void initViews() {
        statusBubbleIndicator = findViewById(R.id.status_bubble_indicator);
        statusAccessibilityIndicator = findViewById(R.id.status_accessibility_indicator);
        statusDataIndicator = findViewById(R.id.status_data_indicator);
        statusBubbleText = findViewById(R.id.status_bubble_text);
        statusAccessibilityText = findViewById(R.id.status_accessibility_text);
        statusDataText = findViewById(R.id.status_data_text);
        iconOverlay = findViewById(R.id.icon_overlay);
        iconAccessibility = findViewById(R.id.icon_accessibility);
        btnToggleBubble = findViewById(R.id.btn_toggle_bubble);
    }
    
    private void setupListeners() {
        // Toggle Bubble button
        btnToggleBubble.setOnClickListener(v -> toggleBubble());
        
        // Open Table button
        findViewById(R.id.btn_open_table).setOnClickListener(v -> {
            startActivity(new Intent(this, TableActivity.class));
        });
        
        // Export button
        findViewById(R.id.btn_export).setOnClickListener(v -> exportData());
        
        // Clear button
        findViewById(R.id.btn_clear).setOnClickListener(v -> showClearDialog());
        
        // Overlay permission click
        findViewById(R.id.permission_overlay).setOnClickListener(v -> requestOverlayPermission());
        
        // Accessibility permission click
        findViewById(R.id.permission_accessibility).setOnClickListener(v -> openAccessibilitySettings());
    }
    
    private void updateUI() {
        // Update bubble status
        boolean bubbleRunning = FloatingBubbleService.isRunning();
        statusBubbleIndicator.setBackgroundResource(
            bubbleRunning ? R.drawable.status_indicator_active : R.drawable.status_indicator_inactive
        );
        statusBubbleText.setText("Floating Bubble: " + (bubbleRunning ? "Active" : "Disabled"));
        btnToggleBubble.setText(bubbleRunning ? R.string.btn_disable_bubble : R.string.btn_enable_bubble);
        
        // Update accessibility status
        boolean accessibilityEnabled = ClipboardAccessibilityService.isAccessibilityServiceEnabled(this);
        statusAccessibilityIndicator.setBackgroundResource(
            accessibilityEnabled ? R.drawable.status_indicator_active : R.drawable.status_indicator_inactive
        );
        statusAccessibilityText.setText("Clipboard Listener: " + (accessibilityEnabled ? "Active" : "Disabled"));
        
        // Update data status
        int rowCount = dataManager.getRowCount();
        int completedCount = dataManager.getCompletedRowCount();
        statusDataText.setText("Data: " + completedCount + "/" + rowCount + " rows complete");
        
        // Update permission icons
        boolean hasOverlay = Settings.canDrawOverlays(this);
        iconOverlay.setImageResource(hasOverlay ? android.R.drawable.ic_menu_save : android.R.drawable.ic_menu_help);
        iconOverlay.setColorFilter(getResources().getColor(hasOverlay ? R.color.success : R.color.text_secondary));
        
        iconAccessibility.setImageResource(accessibilityEnabled ? android.R.drawable.ic_menu_save : android.R.drawable.ic_menu_help);
        iconAccessibility.setColorFilter(getResources().getColor(accessibilityEnabled ? R.color.success : R.color.text_secondary));
    }
    
    private void toggleBubble() {
        if (FloatingBubbleService.isRunning()) {
            // Stop bubble
            Intent intent = new Intent(this, FloatingBubbleService.class);
            intent.setAction(FloatingBubbleService.ACTION_HIDE_BUBBLE);
            startService(intent);
        } else {
            // Check overlay permission first
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please enable overlay permission first", Toast.LENGTH_SHORT).show();
                requestOverlayPermission();
                return;
            }
            
            // Start bubble
            Intent intent = new Intent(this, FloatingBubbleService.class);
            intent.setAction(FloatingBubbleService.ACTION_SHOW_BUBBLE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        
        // Update UI after a short delay
        btnToggleBubble.postDelayed(this::updateUI, 500);
    }
    
    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQUEST_OVERLAY);
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Enable 'Field Data Collector' service", Toast.LENGTH_LONG).show();
    }
    
    private void requestScreenCapture() {
        if (projectionManager != null) {
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            screenCaptureLauncher.launch(captureIntent);
        }
    }
    
    private void exportData() {
        List<FieldData> data = dataManager.getAllData();
        
        if (data.isEmpty() || (data.size() == 1 && !data.get(0).hasAutoData())) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "Exporting...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                File file = ExcelExporter.export(this, data);
                
                runOnUiThread(() -> {
                    if (file != null && file.exists()) {
                        showExportSuccess(file);
                    } else {
                        Toast.makeText(this, getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Export error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void showExportSuccess(File file) {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.toast_export_success))
            .setMessage("File: " + file.getName())
            .setPositiveButton(getString(R.string.btn_share), (d, w) -> shareFile(file))
            .setNegativeButton("OK", null)
            .show();
    }
    
    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_share)));
        } catch (Exception e) {
            Toast.makeText(this, "Share error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showClearDialog() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_clear_title))
            .setMessage(getString(R.string.dialog_clear_message))
            .setPositiveButton("Clear", (d, w) -> {
                dataManager.clearAllData();
                Toast.makeText(this, getString(R.string.toast_data_cleared), Toast.LENGTH_SHORT).show();
                updateUI();
            })
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_OVERLAY) {
            updateUI();
        }
    }
    
    // DataChangeListener callbacks
    @Override
    public void onDataChanged(List<FieldData> data) {
        runOnUiThread(this::updateUI);
    }
    
    @Override
    public void onCurrentRowChanged(int rowIndex, FieldData data) {
        runOnUiThread(this::updateUI);
    }
    
    @Override
    public void onRowCompleted(int rowIndex) {
        runOnUiThread(this::updateUI);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dataManager.removeListener(this);
        
        try {
            unregisterReceiver(captureRequestReceiver);
        } catch (Exception e) {
            // Ignore
        }
    }
}

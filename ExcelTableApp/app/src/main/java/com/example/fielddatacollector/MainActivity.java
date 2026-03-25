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
import android.widget.EditText;
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
    
    // UI Elements - Status indicators
    private View statusClipboard;
    private View statusBubble;
    private TextView txtClipboardStatus;
    private TextView txtBubbleStatus;
    private TextView txtRecordCount;
    
    // Buttons
    private Button btnToggleBubble;
    private Button btnSetupAccessibility;
    private Button btnViewTable;
    private Button btnExportExcel;
    private Button btnShareExcel;
    private Button btnParseManual;
    private Button btnClearInput;
    private EditText editManualInput;
    
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
        // Status indicators
        statusClipboard = findViewById(R.id.statusClipboard);
        statusBubble = findViewById(R.id.statusBubble);
        txtClipboardStatus = findViewById(R.id.txtClipboardStatus);
        txtBubbleStatus = findViewById(R.id.txtBubbleStatus);
        txtRecordCount = findViewById(R.id.txtRecordCount);
        
        // Buttons
        btnToggleBubble = findViewById(R.id.btnToggleBubble);
        btnSetupAccessibility = findViewById(R.id.btnSetupAccessibility);
        btnViewTable = findViewById(R.id.btnViewTable);
        btnExportExcel = findViewById(R.id.btnExportExcel);
        btnShareExcel = findViewById(R.id.btnShareExcel);
        btnParseManual = findViewById(R.id.btnParseManual);
        btnClearInput = findViewById(R.id.btnClearInput);
        editManualInput = findViewById(R.id.editManualInput);
    }
    
    private void setupListeners() {
        // Toggle Bubble button
        btnToggleBubble.setOnClickListener(v -> toggleBubble());
        
        // Setup Accessibility button
        btnSetupAccessibility.setOnClickListener(v -> openAccessibilitySettings());
        
        // View Table button
        btnViewTable.setOnClickListener(v -> {
            startActivity(new Intent(this, TableActivity.class));
        });
        
        // Export Excel button
        btnExportExcel.setOnClickListener(v -> exportData());
        
        // Share Excel button
        btnShareExcel.setOnClickListener(v -> {
            List<FieldData> data = dataManager.getAllData();
            if (data.isEmpty()) {
                Toast.makeText(this, "No data to share", Toast.LENGTH_SHORT).show();
                return;
            }
            exportAndShare();
        });
        
        // Parse Manual button
        btnParseManual.setOnClickListener(v -> parseManualInput());
        
        // Clear Input button
        btnClearInput.setOnClickListener(v -> {
            editManualInput.setText("");
        });
    }
    
    private void parseManualInput() {
        String text = editManualInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter text to parse", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Use MessageParser to parse the text
        MessageParser.ParseResult parseResult = MessageParser.parse(text);
        
        if (parseResult != null && parseResult.isValid) {
            // Create FieldData from ParseResult
            FieldData fieldData = new FieldData();
            fieldData.updateFromMessage(parseResult.bankName, parseResult.applicantName, parseResult.reason);
            
            dataManager.addOrUpdateCurrentRow(fieldData);
            Toast.makeText(this, "Data parsed successfully!", Toast.LENGTH_SHORT).show();
            editManualInput.setText("");
            updateUI();
        } else {
            Toast.makeText(this, "Could not parse the text. Check format.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateUI() {
        // Update bubble status
        boolean bubbleRunning = FloatingBubbleService.isRunning();
        statusBubble.setBackgroundResource(
            bubbleRunning ? R.drawable.status_indicator_active : R.drawable.status_indicator_inactive
        );
        txtBubbleStatus.setText(bubbleRunning ? "Active" : "Disabled");
        btnToggleBubble.setText(bubbleRunning ? "Disable Floating Bubble" : "Enable Floating Bubble");
        
        // Update accessibility status
        boolean accessibilityEnabled = ClipboardAccessibilityService.isAccessibilityServiceEnabled(this);
        statusClipboard.setBackgroundResource(
            accessibilityEnabled ? R.drawable.status_indicator_active : R.drawable.status_indicator_inactive
        );
        txtClipboardStatus.setText(accessibilityEnabled ? "Active" : "Disabled");
        
        // Update record count
        int rowCount = dataManager.getRowCount();
        txtRecordCount.setText(String.valueOf(rowCount));
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
        
        if (data.isEmpty()) {
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
                        Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Export error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void exportAndShare() {
        List<FieldData> data = dataManager.getAllData();
        
        Toast.makeText(this, "Preparing to share...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                File file = ExcelExporter.export(this, data);
                
                runOnUiThread(() -> {
                    if (file != null && file.exists()) {
                        shareFile(file);
                    } else {
                        Toast.makeText(this, "Could not create file", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void showExportSuccess(File file) {
        new AlertDialog.Builder(this)
            .setTitle("Export Successful!")
            .setMessage("File: " + file.getName())
            .setPositiveButton("Share", (d, w) -> shareFile(file))
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
            
            startActivity(Intent.createChooser(shareIntent, "Share Excel File"));
        } catch (Exception e) {
            Toast.makeText(this, "Share error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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

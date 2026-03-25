package com.example.fielddatacollector;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fielddatacollector.model.FieldData;

import java.io.File;
import java.util.List;

/**
 * Activity displaying the full data table with 9 columns.
 * Allows viewing, editing manual fields, and exporting to Excel.
 */
public class TableActivity extends AppCompatActivity implements DataManager.DataChangeListener {
    
    private RecyclerView tableRecycler;
    private TableAdapter adapter;
    private DataManager dataManager;
    private TextView rowCountText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table);
        
        dataManager = DataManager.getInstance(this);
        dataManager.addListener(this);
        
        initViews();
        setupRecyclerView();
        loadData();
    }
    
    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        rowCountText = findViewById(R.id.row_count);
        Button btnAddRow = findViewById(R.id.btn_add_row);
        Button btnExport = findViewById(R.id.btn_export);
        
        btnBack.setOnClickListener(v -> finish());
        
        btnAddRow.setOnClickListener(v -> {
            dataManager.addNewRow();
            adapter.setData(dataManager.getAllData());
            tableRecycler.scrollToPosition(adapter.getItemCount() - 1);
            updateRowCount();
        });
        
        btnExport.setOnClickListener(v -> exportToExcel());
    }
    
    private void setupRecyclerView() {
        tableRecycler = findViewById(R.id.table_recycler);
        adapter = new TableAdapter(dataManager);
        
        tableRecycler.setLayoutManager(new LinearLayoutManager(this));
        tableRecycler.setAdapter(adapter);
    }
    
    private void loadData() {
        List<FieldData> data = dataManager.getAllData();
        adapter.setData(data);
        updateRowCount();
    }
    
    private void updateRowCount() {
        int total = dataManager.getRowCount();
        int completed = dataManager.getCompletedRowCount();
        rowCountText.setText(completed + "/" + total + " complete");
    }
    
    private void exportToExcel() {
        List<FieldData> data = dataManager.getAllData();
        
        if (data.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading
        Toast.makeText(this, "Exporting...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                File exportFile = ExcelExporter.export(this, data);
                
                runOnUiThread(() -> {
                    if (exportFile != null && exportFile.exists()) {
                        showExportSuccessDialog(exportFile);
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
    
    private void showExportSuccessDialog(File file) {
        new AlertDialog.Builder(this)
            .setTitle("Export Successful")
            .setMessage("File saved to:\n" + file.getAbsolutePath())
            .setPositiveButton("Share", (dialog, which) -> shareFile(file))
            .setNegativeButton("OK", null)
            .show();
    }
    
    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                file
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Share Excel file"));
            
        } catch (Exception e) {
            Toast.makeText(this, "Share error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // DataChangeListener callbacks
    @Override
    public void onDataChanged(List<FieldData> data) {
        runOnUiThread(() -> {
            adapter.setData(data);
            updateRowCount();
        });
    }
    
    @Override
    public void onCurrentRowChanged(int rowIndex, FieldData data) {
        runOnUiThread(() -> adapter.updateRow(rowIndex));
    }
    
    @Override
    public void onRowCompleted(int rowIndex) {
        runOnUiThread(this::updateRowCount);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dataManager.removeListener(this);
    }
}

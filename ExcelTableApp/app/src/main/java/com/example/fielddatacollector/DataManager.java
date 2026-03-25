package com.example.fielddatacollector;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.fielddatacollector.model.FieldData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages field data storage, retrieval, and manipulation.
 * Uses SharedPreferences with Gson for persistence.
 */
public class DataManager {
    
    private static final String PREFS_NAME = "FieldDataPrefs";
    private static final String KEY_DATA_LIST = "data_list";
    private static final String KEY_CURRENT_ROW = "current_row";
    
    private static DataManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    
    private List<FieldData> dataList;
    private int currentRowIndex;
    
    // Listener for data changes
    public interface DataChangeListener {
        void onDataChanged(List<FieldData> data);
        void onCurrentRowChanged(int rowIndex, FieldData data);
        void onRowCompleted(int rowIndex);
    }
    
    private final List<DataChangeListener> listeners = new ArrayList<>();
    
    private DataManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadData();
    }
    
    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context);
        }
        return instance;
    }
    
    /**
     * Load data from SharedPreferences
     */
    private void loadData() {
        String json = prefs.getString(KEY_DATA_LIST, null);
        if (json != null && !json.isEmpty()) {
            Type type = new TypeToken<ArrayList<FieldData>>(){}.getType();
            dataList = gson.fromJson(json, type);
        }
        
        if (dataList == null) {
            dataList = new ArrayList<>();
        }
        
        currentRowIndex = prefs.getInt(KEY_CURRENT_ROW, 0);
        
        // Ensure at least one row exists
        if (dataList.isEmpty()) {
            addNewRow();
        }
        
        // Ensure current row index is valid
        if (currentRowIndex >= dataList.size()) {
            currentRowIndex = dataList.size() - 1;
        }
    }
    
    /**
     * Save data to SharedPreferences
     */
    private void saveData() {
        String json = gson.toJson(dataList);
        prefs.edit()
            .putString(KEY_DATA_LIST, json)
            .putInt(KEY_CURRENT_ROW, currentRowIndex)
            .apply();
    }
    
    /**
     * Add a new empty row
     */
    public FieldData addNewRow() {
        int newSrNo = dataList.size() + 1;
        FieldData newData = new FieldData(newSrNo);
        dataList.add(newData);
        saveData();
        notifyDataChanged();
        return newData;
    }
    
    /**
     * Get current row data
     */
    public FieldData getCurrentRow() {
        if (currentRowIndex >= 0 && currentRowIndex < dataList.size()) {
            return dataList.get(currentRowIndex);
        }
        return addNewRow();
    }
    
    /**
     * Get current row index (1-based for display)
     */
    public int getCurrentRowNumber() {
        return currentRowIndex + 1;
    }
    
    /**
     * Move to next row
     */
    public void moveToNextRow() {
        // Check if current row is complete
        FieldData current = getCurrentRow();
        if (current.isRowComplete()) {
            notifyRowCompleted(currentRowIndex);
        }
        
        // Move to next row or create new one
        currentRowIndex++;
        if (currentRowIndex >= dataList.size()) {
            addNewRow();
        }
        
        saveData();
        notifyCurrentRowChanged();
    }
    
    /**
     * Move to specific row
     */
    public void moveToRow(int index) {
        if (index >= 0 && index < dataList.size()) {
            currentRowIndex = index;
            saveData();
            notifyCurrentRowChanged();
        }
    }
    
    /**
     * Update current row with message data
     */
    public void updateCurrentRowFromMessage(String bankName, String applicantName, String reason) {
        FieldData current = getCurrentRow();
        current.updateFromMessage(bankName, applicantName, reason);
        saveData();
        notifyDataChanged();
        notifyCurrentRowChanged();
    }
    
    /**
     * Update current row with OCR lat-long
     */
    public void updateCurrentRowFromOCR(String latLong) {
        FieldData current = getCurrentRow();
        current.updateFromOCR(latLong);
        saveData();
        notifyDataChanged();
        notifyCurrentRowChanged();
        
        // Check if row is now complete
        if (current.isRowComplete()) {
            notifyRowCompleted(currentRowIndex);
        }
    }
    
    /**
     * Update manual field in current row
     */
    public void updateManualField(String field, String value) {
        FieldData current = getCurrentRow();
        
        switch (field.toLowerCase()) {
            case "status":
                current.setStatus(value);
                break;
            case "latlong_from":
            case "latlongfrom":
                current.setLatLongFrom(value);
                break;
            case "area":
                current.setArea(value);
                break;
            case "km":
                current.setKm(value);
                break;
        }
        
        saveData();
        notifyDataChanged();
    }
    
    /**
     * Update a specific row
     */
    public void updateRow(int index, FieldData data) {
        if (index >= 0 && index < dataList.size()) {
            // Preserve Sr No
            data.setSrNo(index + 1);
            dataList.set(index, data);
            saveData();
            notifyDataChanged();
        }
    }
    
    /**
     * Get all data
     */
    public List<FieldData> getAllData() {
        return new ArrayList<>(dataList);
    }
    
    /**
     * Get row by index
     */
    public FieldData getRow(int index) {
        if (index >= 0 && index < dataList.size()) {
            return dataList.get(index);
        }
        return null;
    }
    
    /**
     * Get row count
     */
    public int getRowCount() {
        return dataList.size();
    }
    
    /**
     * Delete a row
     */
    public void deleteRow(int index) {
        if (index >= 0 && index < dataList.size()) {
            dataList.remove(index);
            
            // Update Sr No for remaining rows
            for (int i = index; i < dataList.size(); i++) {
                dataList.get(i).setSrNo(i + 1);
            }
            
            // Adjust current row index
            if (currentRowIndex >= dataList.size()) {
                currentRowIndex = Math.max(0, dataList.size() - 1);
            }
            
            // Ensure at least one row
            if (dataList.isEmpty()) {
                addNewRow();
            }
            
            saveData();
            notifyDataChanged();
            notifyCurrentRowChanged();
        }
    }
    
    /**
     * Clear all data
     */
    public void clearAllData() {
        dataList.clear();
        currentRowIndex = 0;
        addNewRow();
        saveData();
        notifyDataChanged();
        notifyCurrentRowChanged();
    }
    
    /**
     * Get completed row count
     */
    public int getCompletedRowCount() {
        int count = 0;
        for (FieldData data : dataList) {
            if (data.isRowComplete()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Check if current row has message data
     */
    public boolean currentRowHasMessageData() {
        return getCurrentRow().isMessageDataComplete();
    }
    
    /**
     * Check if current row has image data
     */
    public boolean currentRowHasImageData() {
        return getCurrentRow().isImageDataComplete();
    }
    
    /**
     * Get progress state of current row (0=empty, 1=half, 2=full)
     */
    public int getCurrentRowProgressState() {
        return getCurrentRow().getProgressState();
    }
    
    // Listener management
    public void addListener(DataChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(DataChangeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyDataChanged() {
        for (DataChangeListener listener : listeners) {
            listener.onDataChanged(getAllData());
        }
    }
    
    private void notifyCurrentRowChanged() {
        for (DataChangeListener listener : listeners) {
            listener.onCurrentRowChanged(currentRowIndex, getCurrentRow());
        }
    }
    
    private void notifyRowCompleted(int rowIndex) {
        for (DataChangeListener listener : listeners) {
            listener.onRowCompleted(rowIndex);
        }
    }
}

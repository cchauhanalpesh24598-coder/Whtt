package com.example.fielddatacollector;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fielddatacollector.model.FieldData;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for the data table.
 * Displays 9 columns with auto-filled and manually editable fields.
 */
public class TableAdapter extends RecyclerView.Adapter<TableAdapter.RowViewHolder> {
    
    private List<FieldData> dataList = new ArrayList<>();
    private final DataManager dataManager;
    
    public TableAdapter(DataManager dataManager) {
        this.dataManager = dataManager;
    }
    
    public void setData(List<FieldData> data) {
        this.dataList = new ArrayList<>(data);
        notifyDataSetChanged();
    }
    
    public void updateRow(int position) {
        if (position >= 0 && position < dataList.size()) {
            notifyItemChanged(position);
        }
    }
    
    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.table_row_item, parent, false);
        return new RowViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        FieldData data = dataList.get(position);
        holder.bind(data, position);
    }
    
    @Override
    public int getItemCount() {
        return dataList.size();
    }
    
    class RowViewHolder extends RecyclerView.ViewHolder {
        
        private final TextView cellSrNo;
        private final TextView cellBank;
        private final TextView cellApplicant;
        private final EditText cellStatus;
        private final TextView cellReason;
        private final EditText cellLatLongFrom;
        private final TextView cellLatLongTo;
        private final EditText cellArea;
        private final EditText cellKm;
        
        // Text watchers to prevent recursive updates
        private TextWatcher statusWatcher;
        private TextWatcher latLongFromWatcher;
        private TextWatcher areaWatcher;
        private TextWatcher kmWatcher;
        
        RowViewHolder(View itemView) {
            super(itemView);
            
            cellSrNo = itemView.findViewById(R.id.cell_sr_no);
            cellBank = itemView.findViewById(R.id.cell_bank);
            cellApplicant = itemView.findViewById(R.id.cell_applicant);
            cellStatus = itemView.findViewById(R.id.cell_status);
            cellReason = itemView.findViewById(R.id.cell_reason);
            cellLatLongFrom = itemView.findViewById(R.id.cell_latlong_from);
            cellLatLongTo = itemView.findViewById(R.id.cell_latlong_to);
            cellArea = itemView.findViewById(R.id.cell_area);
            cellKm = itemView.findViewById(R.id.cell_km);
        }
        
        void bind(FieldData data, int position) {
            // Remove existing watchers
            removeTextWatchers();
            
            // Set auto-filled fields (read-only display)
            cellSrNo.setText(String.valueOf(data.getSrNo()));
            cellBank.setText(data.getBankName().isEmpty() ? "" : data.getBankName());
            cellApplicant.setText(data.getApplicantName().isEmpty() ? "" : data.getApplicantName());
            cellReason.setText(data.getReasonForCnv().isEmpty() ? "" : data.getReasonForCnv());
            cellLatLongTo.setText(data.getLatLongTo().isEmpty() ? "" : data.getLatLongTo());
            
            // Set manual fields (editable)
            cellStatus.setText(data.getStatus());
            cellLatLongFrom.setText(data.getLatLongFrom());
            cellArea.setText(data.getArea());
            cellKm.setText(data.getKm());
            
            // Set row background based on completion
            int bgColor;
            if (data.isRowComplete()) {
                bgColor = itemView.getContext().getResources().getColor(R.color.cell_auto);
            } else if (position % 2 == 0) {
                bgColor = itemView.getContext().getResources().getColor(R.color.table_row_even);
            } else {
                bgColor = itemView.getContext().getResources().getColor(R.color.table_row_odd);
            }
            
            // Add text watchers for manual fields
            addTextWatchers(position);
        }
        
        private void removeTextWatchers() {
            if (statusWatcher != null) {
                cellStatus.removeTextChangedListener(statusWatcher);
            }
            if (latLongFromWatcher != null) {
                cellLatLongFrom.removeTextChangedListener(latLongFromWatcher);
            }
            if (areaWatcher != null) {
                cellArea.removeTextChangedListener(areaWatcher);
            }
            if (kmWatcher != null) {
                cellKm.removeTextChangedListener(kmWatcher);
            }
        }
        
        private void addTextWatchers(final int position) {
            // Status field watcher
            statusWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateFieldData(position, "status", s.toString());
                }
            };
            cellStatus.addTextChangedListener(statusWatcher);
            
            // Lat-Long From watcher
            latLongFromWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateFieldData(position, "latlong_from", s.toString());
                }
            };
            cellLatLongFrom.addTextChangedListener(latLongFromWatcher);
            
            // Area watcher
            areaWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateFieldData(position, "area", s.toString());
                }
            };
            cellArea.addTextChangedListener(areaWatcher);
            
            // Km watcher
            kmWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateFieldData(position, "km", s.toString());
                }
            };
            cellKm.addTextChangedListener(kmWatcher);
        }
        
        private void updateFieldData(int position, String field, String value) {
            if (position < 0 || position >= dataList.size()) return;
            
            FieldData data = dataList.get(position);
            
            switch (field) {
                case "status":
                    data.setStatus(value);
                    break;
                case "latlong_from":
                    data.setLatLongFrom(value);
                    break;
                case "area":
                    data.setArea(value);
                    break;
                case "km":
                    data.setKm(value);
                    break;
            }
            
            // Save to DataManager
            dataManager.updateRow(position, data);
        }
    }
    
    /**
     * Simplified TextWatcher that only implements afterTextChanged
     */
    abstract class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}

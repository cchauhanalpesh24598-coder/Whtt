package com.example.fielddatacollector.model;

import java.io.Serializable;

/**
 * Data model representing a single row in the field data table.
 * Contains both auto-filled (from WhatsApp/OCR) and manual fields.
 */
public class FieldData implements Serializable {
    
    private int srNo;                    // Auto - Serial Number
    private String bankName;             // Auto - Extracted from message
    private String applicantName;        // Auto - Extracted from message
    private String status;               // Manual - User input
    private String reasonForCnv;         // Auto - Extracted from message
    private String latLongFrom;          // Manual - User input
    private String latLongTo;            // Auto - Extracted from OCR
    private String area;                 // Manual - User input
    private String km;                   // Manual - User input
    
    // Completion tracking
    private boolean messageDataComplete; // True when message data extracted
    private boolean imageDataComplete;   // True when OCR lat-long extracted
    private long timestamp;              // Creation timestamp
    
    public FieldData() {
        this.timestamp = System.currentTimeMillis();
        this.bankName = "";
        this.applicantName = "";
        this.status = "";
        this.reasonForCnv = "";
        this.latLongFrom = "";
        this.latLongTo = "";
        this.area = "";
        this.km = "";
    }
    
    public FieldData(int srNo) {
        this();
        this.srNo = srNo;
    }
    
    // Getters and Setters
    public int getSrNo() {
        return srNo;
    }
    
    public void setSrNo(int srNo) {
        this.srNo = srNo;
    }
    
    public String getBankName() {
        return bankName;
    }
    
    public void setBankName(String bankName) {
        this.bankName = bankName != null ? bankName.trim() : "";
    }
    
    public String getApplicantName() {
        return applicantName;
    }
    
    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName != null ? applicantName.trim() : "";
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status != null ? status.trim() : "";
    }
    
    public String getReasonForCnv() {
        return reasonForCnv;
    }
    
    public void setReasonForCnv(String reasonForCnv) {
        this.reasonForCnv = reasonForCnv != null ? reasonForCnv.trim() : "";
    }
    
    public String getLatLongFrom() {
        return latLongFrom;
    }
    
    public void setLatLongFrom(String latLongFrom) {
        this.latLongFrom = latLongFrom != null ? latLongFrom.trim() : "";
    }
    
    public String getLatLongTo() {
        return latLongTo;
    }
    
    public void setLatLongTo(String latLongTo) {
        this.latLongTo = latLongTo != null ? latLongTo.trim() : "";
    }
    
    public String getArea() {
        return area;
    }
    
    public void setArea(String area) {
        this.area = area != null ? area.trim() : "";
    }
    
    public String getKm() {
        return km;
    }
    
    public void setKm(String km) {
        this.km = km != null ? km.trim() : "";
    }
    
    public boolean isMessageDataComplete() {
        return messageDataComplete;
    }
    
    public void setMessageDataComplete(boolean messageDataComplete) {
        this.messageDataComplete = messageDataComplete;
    }
    
    public boolean isImageDataComplete() {
        return imageDataComplete;
    }
    
    public void setImageDataComplete(boolean imageDataComplete) {
        this.imageDataComplete = imageDataComplete;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Check if both message and image data are complete
     */
    public boolean isRowComplete() {
        return messageDataComplete && imageDataComplete;
    }
    
    /**
     * Check if any auto-fill data is present
     */
    public boolean hasAutoData() {
        return !bankName.isEmpty() || !applicantName.isEmpty() || 
               !reasonForCnv.isEmpty() || !latLongTo.isEmpty();
    }
    
    /**
     * Get progress state for UI display
     * 0 = Empty, 1 = Half (message only), 2 = Full (message + image)
     */
    public int getProgressState() {
        if (messageDataComplete && imageDataComplete) {
            return 2; // Full ring
        } else if (messageDataComplete) {
            return 1; // Half ring
        }
        return 0; // Empty
    }
    
    /**
     * Update message fields
     */
    public void updateFromMessage(String bank, String applicant, String reason) {
        if (bank != null && !bank.isEmpty()) {
            this.bankName = bank.trim();
        }
        if (applicant != null && !applicant.isEmpty()) {
            this.applicantName = applicant.trim();
        }
        if (reason != null && !reason.isEmpty()) {
            this.reasonForCnv = reason.trim();
        }
        // Mark message complete if we have at least applicant name
        if (!this.applicantName.isEmpty()) {
            this.messageDataComplete = true;
        }
    }
    
    /**
     * Update lat-long from OCR
     */
    public void updateFromOCR(String latLong) {
        if (latLong != null && !latLong.isEmpty()) {
            this.latLongTo = latLong.trim();
            this.imageDataComplete = true;
        }
    }
    
    @Override
    public String toString() {
        return "FieldData{" +
                "srNo=" + srNo +
                ", bankName='" + bankName + '\'' +
                ", applicantName='" + applicantName + '\'' +
                ", status='" + status + '\'' +
                ", reasonForCnv='" + reasonForCnv + '\'' +
                ", latLongFrom='" + latLongFrom + '\'' +
                ", latLongTo='" + latLongTo + '\'' +
                ", area='" + area + '\'' +
                ", km='" + km + '\'' +
                ", messageComplete=" + messageDataComplete +
                ", imageComplete=" + imageDataComplete +
                '}';
    }
}

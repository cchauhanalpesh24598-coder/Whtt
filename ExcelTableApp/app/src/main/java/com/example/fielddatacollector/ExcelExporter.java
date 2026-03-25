package com.example.fielddatacollector;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.example.fielddatacollector.model.FieldData;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class to export field data to Excel (.xlsx) format.
 * Uses Apache POI library for Excel file generation.
 */
public class ExcelExporter {
    
    private static final String TAG = "ExcelExporter";
    
    // Column headers
    private static final String[] HEADERS = {
        "Sr No",
        "Bank Name",
        "Applicant Name",
        "Status",
        "Reason for Cnv",
        "Lat-Long From",
        "Lat-Long To",
        "Area",
        "Km"
    };
    
    // Column widths (in characters * 256)
    private static final int[] COLUMN_WIDTHS = {
        8 * 256,    // Sr No
        15 * 256,   // Bank Name
        25 * 256,   // Applicant Name
        12 * 256,   // Status
        12 * 256,   // Reason
        20 * 256,   // Lat-Long From
        20 * 256,   // Lat-Long To
        15 * 256,   // Area
        10 * 256    // Km
    };
    
    /**
     * Export data to Excel file
     * @param context Application context
     * @param dataList List of FieldData to export
     * @return File object of the created Excel file, or null if failed
     */
    public static File export(Context context, List<FieldData> dataList) {
        Workbook workbook = null;
        FileOutputStream outputStream = null;
        
        try {
            // Create workbook and sheet
            workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Field Data");
            
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle autoFillStyle = createAutoFillStyle(workbook);
            CellStyle manualStyle = createManualStyle(workbook);
            
            // Set column widths
            for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i]);
            }
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            int rowNum = 1;
            for (FieldData data : dataList) {
                Row row = sheet.createRow(rowNum++);
                
                // Sr No (Auto)
                Cell cellSrNo = row.createCell(0);
                cellSrNo.setCellValue(data.getSrNo());
                cellSrNo.setCellStyle(autoFillStyle);
                
                // Bank Name (Auto)
                Cell cellBank = row.createCell(1);
                cellBank.setCellValue(data.getBankName());
                cellBank.setCellStyle(autoFillStyle);
                
                // Applicant Name (Auto)
                Cell cellApplicant = row.createCell(2);
                cellApplicant.setCellValue(data.getApplicantName());
                cellApplicant.setCellStyle(autoFillStyle);
                
                // Status (Manual)
                Cell cellStatus = row.createCell(3);
                cellStatus.setCellValue(data.getStatus());
                cellStatus.setCellStyle(manualStyle);
                
                // Reason (Auto)
                Cell cellReason = row.createCell(4);
                cellReason.setCellValue(data.getReasonForCnv());
                cellReason.setCellStyle(autoFillStyle);
                
                // Lat-Long From (Manual)
                Cell cellLatFrom = row.createCell(5);
                cellLatFrom.setCellValue(data.getLatLongFrom());
                cellLatFrom.setCellStyle(manualStyle);
                
                // Lat-Long To (Auto from OCR)
                Cell cellLatTo = row.createCell(6);
                cellLatTo.setCellValue(data.getLatLongTo());
                cellLatTo.setCellStyle(autoFillStyle);
                
                // Area (Manual)
                Cell cellArea = row.createCell(7);
                cellArea.setCellValue(data.getArea());
                cellArea.setCellStyle(manualStyle);
                
                // Km (Manual)
                Cell cellKm = row.createCell(8);
                cellKm.setCellValue(data.getKm());
                cellKm.setCellStyle(manualStyle);
            }
            
            // Generate filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
            String fileName = "FieldData_" + timestamp + ".xlsx";
            
            // Get output directory
            File outputDir = getOutputDirectory(context);
            if (outputDir == null) {
                Log.e(TAG, "Could not get output directory");
                return null;
            }
            
            File outputFile = new File(outputDir, fileName);
            
            // Write to file
            outputStream = new FileOutputStream(outputFile);
            workbook.write(outputStream);
            
            Log.d(TAG, "Excel exported to: " + outputFile.getAbsolutePath());
            return outputFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting to Excel", e);
            return null;
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (workbook != null) {
                    workbook.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing resources", e);
            }
        }
    }
    
    /**
     * Get output directory for Excel files
     */
    private static File getOutputDirectory(Context context) {
        // Try Downloads folder first
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs())) {
            return downloadsDir;
        }
        
        // Fall back to app's external files directory
        File appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (appDir != null && (appDir.exists() || appDir.mkdirs())) {
            return appDir;
        }
        
        // Last resort: internal storage
        File internalDir = new File(context.getFilesDir(), "exports");
        if (internalDir.exists() || internalDir.mkdirs()) {
            return internalDir;
        }
        
        return null;
    }
    
    /**
     * Create header cell style
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // Font
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        
        // Background
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }
    
    /**
     * Create standard data cell style
     */
    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        return style;
    }
    
    /**
     * Create auto-filled cell style (light green background)
     */
    private static CellStyle createAutoFillStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    /**
     * Create manual input cell style (light orange background)
     */
    private static CellStyle createManualStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}

package com.example.fielddatacollector;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.fielddatacollector.utils.LatLongExtractor;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/**
 * Handles OCR (Optical Character Recognition) processing using Google ML Kit.
 * Extracts text from images and parses lat-long coordinates.
 */
public class OCRProcessor {
    
    private static final String TAG = "OCRProcessor";
    
    /**
     * Callback interface for OCR results
     */
    public interface OCRCallback {
        void onSuccess(String extractedText, String latLong);
        void onFailure(String error);
    }
    
    /**
     * Process a bitmap image and extract text
     */
    public static void processImage(Bitmap bitmap, OCRCallback callback) {
        if (bitmap == null) {
            callback.onFailure("Image is null");
            return;
        }
        
        try {
            // Create InputImage from bitmap
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            
            // Get TextRecognizer instance
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            
            // Process the image
            recognizer.process(image)
                .addOnSuccessListener(text -> {
                    String extractedText = text.getText();
                    Log.d(TAG, "OCR extracted text: " + extractedText);
                    
                    // Extract lat-long from the text
                    LatLongExtractor.ExtractionResult result = LatLongExtractor.extract(extractedText);
                    
                    if (result.success) {
                        Log.d(TAG, "Lat-Long found: " + result.formatted);
                        callback.onSuccess(extractedText, result.formatted);
                    } else {
                        // Try processing text blocks individually
                        String latLong = processTextBlocks(text);
                        if (latLong != null && !latLong.isEmpty()) {
                            callback.onSuccess(extractedText, latLong);
                        } else {
                            callback.onFailure("Could not find coordinates in image");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR failed", e);
                    callback.onFailure("OCR error: " + e.getMessage());
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            callback.onFailure("Processing error: " + e.getMessage());
        }
    }
    
    /**
     * Process individual text blocks to find coordinates
     * GPS camera apps often put coordinates in a specific area (usually bottom)
     */
    private static String processTextBlocks(Text text) {
        // Process blocks from bottom to top (GPS info usually at bottom)
        java.util.List<Text.TextBlock> blocks = text.getTextBlocks();
        
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Text.TextBlock block = blocks.get(i);
            String blockText = block.getText();
            
            // Try to extract lat-long from this block
            LatLongExtractor.ExtractionResult result = LatLongExtractor.extract(blockText);
            if (result.success) {
                Log.d(TAG, "Found lat-long in block: " + result.formatted);
                return result.formatted;
            }
            
            // Also check individual lines within the block
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText();
                result = LatLongExtractor.extract(lineText);
                if (result.success) {
                    Log.d(TAG, "Found lat-long in line: " + result.formatted);
                    return result.formatted;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Process image with focus on bottom portion (where GPS data usually is)
     */
    public static void processImageBottomFocus(Bitmap bitmap, OCRCallback callback) {
        if (bitmap == null) {
            callback.onFailure("Image is null");
            return;
        }
        
        // Crop bottom 30% of image where GPS info usually appears
        int cropHeight = (int) (bitmap.getHeight() * 0.3);
        int cropY = bitmap.getHeight() - cropHeight;
        
        try {
            Bitmap croppedBitmap = Bitmap.createBitmap(
                bitmap, 0, cropY, 
                bitmap.getWidth(), cropHeight
            );
            
            // Process the cropped image
            processImage(croppedBitmap, new OCRCallback() {
                @Override
                public void onSuccess(String extractedText, String latLong) {
                    croppedBitmap.recycle();
                    callback.onSuccess(extractedText, latLong);
                }
                
                @Override
                public void onFailure(String error) {
                    croppedBitmap.recycle();
                    // If cropped image fails, try full image
                    processImage(bitmap, callback);
                }
            });
            
        } catch (Exception e) {
            // Fall back to full image processing
            processImage(bitmap, callback);
        }
    }
    
    /**
     * Validate and format extracted lat-long
     */
    public static String formatLatLong(String lat, String lon) {
        if (lat == null || lon == null || lat.isEmpty() || lon.isEmpty()) {
            return "";
        }
        
        try {
            // Validate ranges
            double latitude = Double.parseDouble(lat);
            double longitude = Double.parseDouble(lon);
            
            if (latitude < -90 || latitude > 90) return "";
            if (longitude < -180 || longitude > 180) return "";
            
            // Format to consistent decimal places
            return String.format("%.4f,%.4f", latitude, longitude);
            
        } catch (NumberFormatException e) {
            return lat + "," + lon;
        }
    }
}

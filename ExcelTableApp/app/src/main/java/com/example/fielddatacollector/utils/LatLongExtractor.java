package com.example.fielddatacollector.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for extracting Latitude-Longitude coordinates from OCR text.
 * 
 * GPS Camera photos typically show coordinates in format:
 * "22.1234N 71.1234E" or "22.1234°N 71.1234°E"
 * 
 * Output format: "22.1234,71.1234"
 */
public class LatLongExtractor {
    
    // Pattern 1: Standard GPS format - 22.1234N 71.1234E
    private static final Pattern PATTERN_STANDARD = Pattern.compile(
        "(\\d{1,2}\\.\\d{2,6})\\s*[°]?\\s*[NS]\\s+(\\d{1,3}\\.\\d{2,6})\\s*[°]?\\s*[EW]",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern 2: With degree symbol - 22°07'24"N 71°19'48"E (DMS format)
    private static final Pattern PATTERN_DMS = Pattern.compile(
        "(\\d{1,2})[°]\\s*(\\d{1,2})[']\\s*(\\d{1,2}(?:\\.\\d+)?)[\"']?\\s*[NS]\\s+" +
        "(\\d{1,3})[°]\\s*(\\d{1,2})[']\\s*(\\d{1,2}(?:\\.\\d+)?)[\"']?\\s*[EW]",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern 3: Decimal without direction - 22.1234, 71.1234
    private static final Pattern PATTERN_DECIMAL = Pattern.compile(
        "(\\d{1,2}\\.\\d{2,6})\\s*,\\s*(\\d{1,3}\\.\\d{2,6})"
    );
    
    // Pattern 4: GPS format with comma - N 22.1234, E 71.1234
    private static final Pattern PATTERN_PREFIX = Pattern.compile(
        "[NS]\\s*(\\d{1,2}\\.\\d{2,6})\\s*,?\\s*[EW]\\s*(\\d{1,3}\\.\\d{2,6})",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern 5: Latitude Longitude labels
    private static final Pattern PATTERN_LABELED = Pattern.compile(
        "(?:lat(?:itude)?[:\\s]+)(\\d{1,2}\\.\\d{2,6}).*?(?:lon(?:g(?:itude)?)?[:\\s]+)(\\d{1,3}\\.\\d{2,6})",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Result class for extraction
     */
    public static class ExtractionResult {
        public String latitude = "";
        public String longitude = "";
        public String formatted = "";
        public boolean success = false;
        public String rawMatch = "";
        
        public ExtractionResult() {}
        
        public ExtractionResult(String lat, String lon) {
            this.latitude = lat;
            this.longitude = lon;
            this.formatted = lat + "," + lon;
            this.success = true;
        }
    }
    
    /**
     * Main extraction method - tries all patterns
     */
    public static ExtractionResult extract(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            return new ExtractionResult();
        }
        
        // Clean up OCR text
        String cleanText = cleanOCRText(ocrText);
        
        // Try each pattern in order of priority
        ExtractionResult result;
        
        // Pattern 1: Standard GPS format (most common)
        result = tryPattern(cleanText, PATTERN_STANDARD, 1, 2);
        if (result.success) {
            result.rawMatch = "Standard format";
            return result;
        }
        
        // Pattern 2: DMS format (convert to decimal)
        result = tryDMSPattern(cleanText);
        if (result.success) {
            result.rawMatch = "DMS format";
            return result;
        }
        
        // Pattern 3: Simple decimal format
        result = tryPattern(cleanText, PATTERN_DECIMAL, 1, 2);
        if (result.success) {
            result.rawMatch = "Decimal format";
            return result;
        }
        
        // Pattern 4: Prefix format (N/E before numbers)
        result = tryPattern(cleanText, PATTERN_PREFIX, 1, 2);
        if (result.success) {
            result.rawMatch = "Prefix format";
            return result;
        }
        
        // Pattern 5: Labeled format
        result = tryPattern(cleanText, PATTERN_LABELED, 1, 2);
        if (result.success) {
            result.rawMatch = "Labeled format";
            return result;
        }
        
        return new ExtractionResult();
    }
    
    /**
     * Try to match a pattern and extract lat/long
     */
    private static ExtractionResult tryPattern(String text, Pattern pattern, int latGroup, int lonGroup) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                String lat = matcher.group(latGroup).trim();
                String lon = matcher.group(lonGroup).trim();
                
                // Validate coordinates
                if (isValidLatitude(lat) && isValidLongitude(lon)) {
                    return new ExtractionResult(lat, lon);
                }
            } catch (Exception e) {
                // Pattern matched but groups invalid
            }
        }
        return new ExtractionResult();
    }
    
    /**
     * Try DMS pattern and convert to decimal
     */
    private static ExtractionResult tryDMSPattern(String text) {
        Matcher matcher = PATTERN_DMS.matcher(text);
        if (matcher.find()) {
            try {
                // Latitude DMS
                double latDeg = Double.parseDouble(matcher.group(1));
                double latMin = Double.parseDouble(matcher.group(2));
                double latSec = Double.parseDouble(matcher.group(3));
                double lat = dmsToDecimal(latDeg, latMin, latSec);
                
                // Longitude DMS
                double lonDeg = Double.parseDouble(matcher.group(4));
                double lonMin = Double.parseDouble(matcher.group(5));
                double lonSec = Double.parseDouble(matcher.group(6));
                double lon = dmsToDecimal(lonDeg, lonMin, lonSec);
                
                // Format to 4 decimal places
                String latStr = String.format("%.4f", lat);
                String lonStr = String.format("%.4f", lon);
                
                return new ExtractionResult(latStr, lonStr);
            } catch (Exception e) {
                // Conversion failed
            }
        }
        return new ExtractionResult();
    }
    
    /**
     * Convert DMS (Degrees, Minutes, Seconds) to Decimal
     */
    private static double dmsToDecimal(double degrees, double minutes, double seconds) {
        return degrees + (minutes / 60.0) + (seconds / 3600.0);
    }
    
    /**
     * Clean OCR text for better pattern matching
     */
    private static String cleanOCRText(String text) {
        // Replace common OCR errors
        text = text.replace("°", "°")
                   .replace("'", "'")
                   .replace("″", "\"")
                   .replace("′", "'")
                   .replace("О", "0")  // Cyrillic O to zero
                   .replace("О", "O")
                   .replace("l", "1")  // lowercase L that might be 1
                   .replaceAll("[\\s]+", " "); // normalize spaces
        
        return text;
    }
    
    /**
     * Validate latitude value (0-90)
     */
    private static boolean isValidLatitude(String lat) {
        try {
            double value = Double.parseDouble(lat);
            return value >= 0 && value <= 90;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validate longitude value (0-180)
     */
    private static boolean isValidLongitude(String lon) {
        try {
            double value = Double.parseDouble(lon);
            return value >= 0 && value <= 180;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Format coordinates to standard output format
     */
    public static String format(String lat, String lon) {
        if (lat == null || lon == null || lat.isEmpty() || lon.isEmpty()) {
            return "";
        }
        return lat + "," + lon;
    }
    
    /**
     * Quick check if text might contain coordinates
     */
    public static boolean mightContainCoordinates(String text) {
        if (text == null || text.isEmpty()) return false;
        
        // Check for coordinate indicators
        String lower = text.toLowerCase();
        return (lower.contains("n") || lower.contains("s")) &&
               (lower.contains("e") || lower.contains("w")) &&
               text.matches(".*\\d+\\.\\d+.*");
    }
}

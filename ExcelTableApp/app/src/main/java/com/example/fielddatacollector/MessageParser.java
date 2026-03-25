package com.example.fielddatacollector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for WhatsApp messages to extract Bank Name, Applicant Name, and Reason.
 * 
 * Example message format:
 * R.v(Fedbank)
 * 1)applicant:- Rahulbhai Jivrajbhi mehta
 * 2)malnar : self
 * ...
 */
public class MessageParser {
    
    /**
     * Result class containing extracted data
     */
    public static class ParseResult {
        public String bankName = "";
        public String applicantName = "";
        public String reason = "";
        public boolean isValid = false;
        
        @Override
        public String toString() {
            return "ParseResult{bank='" + bankName + "', applicant='" + applicantName + 
                   "', reason='" + reason + "', valid=" + isValid + "}";
        }
    }
    
    // Pattern for Bank Name: Extract text inside brackets e.g., R.v(Fedbank) -> Fedbank
    private static final Pattern BANK_PATTERN = Pattern.compile(
        "\\(([A-Za-z0-9\\s]+)\\)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern for Reason: Extract text before bracket e.g., R.v(Fedbank) -> R.v
    private static final Pattern REASON_PATTERN = Pattern.compile(
        "^\\s*([A-Za-z][A-Za-z0-9.]*?)\\s*\\(",
        Pattern.MULTILINE
    );
    
    // Multiple patterns for Applicant Name to handle variations and typos
    private static final Pattern[] APPLICANT_PATTERNS = {
        // Standard format: applicant:- Name or applicant :- Name
        Pattern.compile(
            "(?i)applicant\\s*(?:name)?\\s*[:\\-]+\\s*(.+?)(?:\\n|$)",
            Pattern.MULTILINE
        ),
        // With number prefix: 1)applicant:- Name
        Pattern.compile(
            "(?i)\\d+\\s*\\)?\\s*applicant\\s*(?:name)?\\s*[:\\-]+\\s*(.+?)(?:\\n|$)",
            Pattern.MULTILINE
        ),
        // Typo handling: applicat, aplplicant, etc.
        Pattern.compile(
            "(?i)app?l?i?cant?\\s*(?:name)?\\s*[:\\-]+\\s*(.+?)(?:\\n|$)",
            Pattern.MULTILINE
        ),
        // Simple format: Name: value
        Pattern.compile(
            "(?i)(?:applicant|applicat|name)\\s*[:\\-]\\s*(.+?)(?:\\n|$)",
            Pattern.MULTILINE
        )
    };
    
    /**
     * Parse WhatsApp message and extract relevant data
     */
    public static ParseResult parse(String message) {
        ParseResult result = new ParseResult();
        
        if (message == null || message.trim().isEmpty()) {
            return result;
        }
        
        // Clean up the message
        String cleanMessage = message.trim();
        
        // Extract Bank Name
        result.bankName = extractBankName(cleanMessage);
        
        // Extract Reason
        result.reason = extractReason(cleanMessage);
        
        // Extract Applicant Name
        result.applicantName = extractApplicantName(cleanMessage);
        
        // Mark as valid if we got at least applicant name or bank name
        result.isValid = !result.applicantName.isEmpty() || !result.bankName.isEmpty();
        
        return result;
    }
    
    /**
     * Extract bank name from text inside brackets
     * Example: "R.v(Fedbank)" -> "Fedbank"
     */
    private static String extractBankName(String message) {
        Matcher matcher = BANK_PATTERN.matcher(message);
        if (matcher.find()) {
            String bank = matcher.group(1).trim();
            // Capitalize first letter of each word
            return capitalizeWords(bank);
        }
        return "";
    }
    
    /**
     * Extract reason code from text before brackets
     * Example: "R.v(Fedbank)" -> "R.v"
     */
    private static String extractReason(String message) {
        Matcher matcher = REASON_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
    
    /**
     * Extract applicant name using multiple patterns
     * Handles variations like "applicant:-", "Applicant Name:", "1)applicant:-"
     */
    private static String extractApplicantName(String message) {
        for (Pattern pattern : APPLICANT_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                // Clean up the name
                name = cleanApplicantName(name);
                if (!name.isEmpty()) {
                    return capitalizeWords(name);
                }
            }
        }
        return "";
    }
    
    /**
     * Clean applicant name - remove extra characters and validate
     */
    private static String cleanApplicantName(String name) {
        if (name == null) return "";
        
        // Remove leading/trailing special characters
        name = name.replaceAll("^[^A-Za-z]+", "");
        name = name.replaceAll("[^A-Za-z\\s]+$", "");
        
        // Remove multiple spaces
        name = name.replaceAll("\\s+", " ");
        
        // Trim
        name = name.trim();
        
        // Validate - should have at least 2 characters
        if (name.length() < 2) {
            return "";
        }
        
        return name;
    }
    
    /**
     * Capitalize first letter of each word
     */
    private static String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;
        
        StringBuilder result = new StringBuilder();
        String[] words = text.toLowerCase().split("\\s+");
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Calculate similarity between two strings (Levenshtein-based)
     * Used for fuzzy matching of field names
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        
        if (s1.equals(s2)) return 1.0;
        
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1),     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Check if text looks like a WhatsApp field report message
     */
    public static boolean isFieldReportMessage(String message) {
        if (message == null || message.isEmpty()) return false;
        
        String lower = message.toLowerCase();
        
        // Check for common indicators
        boolean hasApplicant = lower.contains("applicant") || lower.contains("applicat");
        boolean hasBracketPattern = message.contains("(") && message.contains(")");
        boolean hasFieldMarkers = lower.contains(":-") || lower.contains(": ");
        
        return hasApplicant || (hasBracketPattern && hasFieldMarkers);
    }
}

package com.chanakya.shl.model.enums;

public enum ShlFlag {
    L,  // Long-term link
    P,  // Passcode protected
    U;  // Single-use (direct file access)

    public static String toFlagString(boolean longTerm, boolean passcode, boolean singleUse) {
        StringBuilder sb = new StringBuilder();
        if (longTerm) sb.append("L");
        if (passcode) sb.append("P");
        if (singleUse) sb.append("U");
        return sb.toString();
    }
}

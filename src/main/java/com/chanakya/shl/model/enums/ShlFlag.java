package com.chanakya.shl.model.enums;

public enum ShlFlag {
    L,  // Long-term link
    P,  // Passcode protected
    U;  // Direct file access (bypasses manifest)

    public static String toFlagString(boolean longTerm, boolean passcode, boolean directAccess) {
        StringBuilder sb = new StringBuilder();
        if (longTerm) sb.append("L");
        if (passcode) sb.append("P");
        if (directAccess) sb.append("U");
        return sb.toString();
    }
}

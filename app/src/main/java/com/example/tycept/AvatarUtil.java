package com.example.tycept;

public class AvatarUtil {

    // A palette similar to what messaging apps use for auto-generated avatar colors.
    private static final int[] PALETTE = {
            0xFF8B5CF6, // purple
            0xFF3B82F6, // blue
            0xFF06B6D4, // cyan
            0xFF10B981, // green
            0xFFF59E0B, // amber
            0xFFEF4444, // red
            0xFFEC4899, // pink
            0xFF6366F1, // indigo
    };

    public static int colorForName(String name) {
        if (name == null || name.isEmpty()) return PALETTE[0];
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = name.charAt(i) + ((hash << 5) - hash);
        }
        int index = Math.abs(hash) % PALETTE.length;
        return PALETTE[index];
    }

    public static String initialForName(String name) {
        if (name == null || name.isEmpty()) return "?";
        return name.substring(0, 1).toUpperCase();
    }
}

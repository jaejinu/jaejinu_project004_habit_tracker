package com.habit.domain.badge;

public final class BadgeTextWidth {

    private BadgeTextWidth() {
    }

    static int estimate(String s) {
        boolean hasKoreanChars = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) {
                hasKoreanChars = true;
                break;
            }
        }
        return (int) Math.ceil(s.length() * 6.5) + (hasKoreanChars ? s.length() * 4 : 0);
    }
}

package com.habit.domain.badge;

public record CalendarOptions(String fillColor, String emptyColor, String bgColor, String textColor) {

    public static CalendarOptions light(String fillColor) {
        return new CalendarOptions(fillColor, "#ebedf0", "#ffffff", "#24292f");
    }

    public static CalendarOptions dark(String fillColor) {
        return new CalendarOptions(fillColor, "#161b22", "#0d1117", "#c9d1d9");
    }
}

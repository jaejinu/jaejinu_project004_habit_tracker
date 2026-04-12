package com.habit.domain.badge;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CalendarSvgGenerator {

    private static final int COLS = 53;
    private static final int ROWS = 7;
    private static final int CELL = 11;
    private static final int STEP = 14;
    private static final int PAD = 15;
    private static final int WIDTH = COLS * STEP + 30;
    private static final int HEIGHT = ROWS * STEP + 30;
    private static final int WINDOW_DAYS = 364;

    public String generate(List<LocalDate> checkedDates, LocalDate anchor, CalendarOptions options) {
        Set<LocalDate> checkedSet = new HashSet<>(checkedDates);
        LocalDate windowStart = anchor.minusDays(WINDOW_DAYS);
        int anchorDayRow = sundayIndex(anchor.getDayOfWeek());

        StringBuilder sb = new StringBuilder(32768);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(WIDTH)
                .append("\" height=\"")
                .append(HEIGHT)
                .append("\" viewBox=\"0 0 ")
                .append(WIDTH)
                .append(' ')
                .append(HEIGHT)
                .append("\" role=\"img\" aria-label=\"habit calendar\">");
        sb.append("\n<rect x=\"0\" y=\"0\" width=\"")
                .append(WIDTH)
                .append("\" height=\"")
                .append(HEIGHT)
                .append("\" fill=\"")
                .append(escape(options.bgColor()))
                .append("\"/>");

        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                LocalDate date = anchor.minusDays((long) (COLS - 1 - col) * 7).minusDays(anchorDayRow - row);
                int x = PAD + col * STEP;
                int y = PAD + row * STEP;

                boolean inWindow = !date.isAfter(anchor) && !date.isBefore(windowStart);
                String fill;
                boolean checked = false;
                if (!inWindow) {
                    fill = "transparent";
                } else {
                    checked = checkedSet.contains(date);
                    fill = checked ? options.fillColor() : options.emptyColor();
                }

                String title = date.toString() + " \u2014 " + (checked ? "checked" : "no check-in");

                sb.append("\n<rect x=\"")
                        .append(x)
                        .append("\" y=\"")
                        .append(y)
                        .append("\" width=\"")
                        .append(CELL)
                        .append("\" height=\"")
                        .append(CELL)
                        .append("\" rx=\"2\" fill=\"")
                        .append(escape(fill))
                        .append("\"><title>")
                        .append(escape(title))
                        .append("</title></rect>");
            }
        }

        sb.append("\n</svg>");
        return sb.toString();
    }

    private static int sundayIndex(DayOfWeek dow) {
        return switch (dow) {
            case SUNDAY -> 0;
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
        };
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}

package com.habit.domain.badge;

import org.springframework.stereotype.Component;

@Component
public class StreakBadgeGenerator {

    public String generate(int currentStreak, StreakBadgeOptions options) {
        String lang = options.lang();
        String theme = options.theme();
        boolean ko = "ko".equals(lang);
        boolean dark = "dark".equals(theme);

        String labelText;
        if (options.label() != null && !options.label().isBlank()) {
            labelText = options.label();
        } else {
            labelText = ko ? "연속" : "streak";
        }

        String valueText = ko ? (currentStreak + "일") : String.valueOf(currentStreak);

        String labelBg = dark ? "#30363d" : "#555";
        String valueBg;
        if (currentStreak <= 2) {
            valueBg = "#8b949e";
        } else if (currentStreak <= 6) {
            valueBg = "#eab308";
        } else if (currentStreak <= 29) {
            valueBg = "#f97316";
        } else {
            valueBg = "#dc2626";
        }

        int labelWidth = 16 + BadgeTextWidth.estimate(labelText);
        int valueWidth = 16 + BadgeTextWidth.estimate(valueText);
        int baseWidth = labelWidth + valueWidth;
        int totalWidth = Math.max(options.minWidth(), baseWidth);
        if (totalWidth > baseWidth) {
            valueWidth += (totalWidth - baseWidth);
        }

        int labelCenter = labelWidth / 2;
        int valueCenter = labelWidth + valueWidth / 2;

        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(totalWidth)
          .append("\" height=\"20\" viewBox=\"0 0 ").append(totalWidth)
          .append(" 20\" role=\"img\" aria-label=\"").append(labelText).append(": ").append(valueText).append("\">");
        sb.append("<clipPath id=\"r\"><rect width=\"").append(totalWidth).append("\" height=\"20\" rx=\"3\"/></clipPath>");
        sb.append("<g clip-path=\"url(#r)\">");
        sb.append("<rect width=\"").append(totalWidth).append("\" height=\"20\" rx=\"3\" fill=\"").append(labelBg).append("\"/>");
        sb.append("<rect x=\"").append(labelWidth).append("\" width=\"").append(valueWidth).append("\" height=\"20\" fill=\"").append(valueBg).append("\"/>");
        sb.append("</g>");
        sb.append("<g font-family=\"Verdana,Geneva,DejaVu Sans,sans-serif\" font-size=\"11\">");
        sb.append("<text x=\"").append(labelCenter).append("\" y=\"15\" text-anchor=\"middle\" dominant-baseline=\"central\" fill=\"#010101\" fill-opacity=\"0.3\">").append(labelText).append("</text>");
        sb.append("<text x=\"").append(labelCenter).append("\" y=\"14\" text-anchor=\"middle\" dominant-baseline=\"central\" fill=\"#fff\">").append(labelText).append("</text>");
        sb.append("<text x=\"").append(valueCenter).append("\" y=\"15\" text-anchor=\"middle\" dominant-baseline=\"central\" fill=\"#010101\" fill-opacity=\"0.3\">").append(valueText).append("</text>");
        sb.append("<text x=\"").append(valueCenter).append("\" y=\"14\" text-anchor=\"middle\" dominant-baseline=\"central\" fill=\"#fff\">").append(valueText).append("</text>");
        sb.append("</g>");
        sb.append("</svg>");
        return sb.toString();
    }
}

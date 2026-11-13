package com.writingassistant.server;

import com.google.gson.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Pure snapshot rendering: none of these paths invoke extraction or evidence analysis. */
final class Reports {
    private Reports() {}
    static String html(JsonObject report) {
        StringBuilder out = new StringBuilder("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Originality report</title><body><h1>");
        out.append(escape(text(report, "title"))).append("</h1><p>Evidence is not a misconduct determination.</p><dl>");
        for (String key : List.of("engineVersion", "profile", "wordCount", "exactOverlap", "nearOverlap", "totalOverlap", "semanticStatus", "aiWritingStatus"))
            out.append("<dt>").append(escape(key)).append("</dt><dd>").append(escape(text(evidence(report), key))).append("</dd>");
        out.append("</dl><h2>Matches</h2>");
        for (JsonElement value : matches(report)) {
            JsonObject m = value.getAsJsonObject();
            out.append("<section><h3>").append(escape(text(m, "sourceTitle"))).append("</h3><p>")
                    .append(escape(text(m, "method"))).append("</p>");
            String resolver = text(m, "resolverUrl");
            if (resolver.isBlank()) resolver = text(m, "persistentResolverUrl");
            if (!resolver.isBlank()) {
                out.append("<p><a href=\"").append(escape(resolver)).append("\" target=\"_blank\" rel=\"noopener noreferrer\">Publisher Record</a></p>");
            }
            out.append("<blockquote>")
                    .append(escape(text(m, "submittedPassage"))).append("</blockquote><blockquote>")
                    .append(escape(text(m, "sourcePassage"))).append("</blockquote></section>");
        }
        return out.append("<h2>Citations</h2><pre>").append(escape(text(evidence(report), "citations")))
                .append("</pre><h2>Limitations</h2><pre>").append(escape(text(evidence(report), "limitations")))
                .append("</pre><h2>Submitted document</h2><pre>").append(escape(text(report, "documentText")))
                .append("</pre></body></html>").toString();
    }
    static String csv(JsonObject report) {
        StringBuilder out = new StringBuilder("\"sourceId\",\"sourceTitle\",\"sourceType\",\"method\",\"value\",\"submittedPassage\",\"sourcePassage\",\"start\",\"end\",\"citationObservation\"\r\n");
        List<String> keys = List.of("sourceId", "sourceTitle", "sourceType", "method", "value", "submittedPassage", "sourcePassage", "start", "end", "citationObservation");
        for (JsonElement value : matches(report)) {
            JsonObject match = value.getAsJsonObject();
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                out.append(csvCell(text(match, keys.get(i))));
            }
            out.append("\r\n");
        }
        return out.toString();
    }
    static String csvCell(String value) {
        int offset = 0;
        while (offset < value.length() && (Character.isWhitespace(value.charAt(offset))
                || Character.isISOControl(value.charAt(offset)) || value.charAt(offset) == '\uFEFF')) offset++;
        if (offset < value.length() && "=+-@".indexOf(value.charAt(offset)) >= 0
                || !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }
    private static JsonObject evidence(JsonObject report) {
        return report.has("result") && report.get("result").isJsonObject() ? report.getAsJsonObject("result") : report;
    }
    private static JsonArray matches(JsonObject report) {
        JsonObject result = evidence(report);
        return result.has("matches") && result.get("matches").isJsonArray() ? result.getAsJsonArray("matches") : new JsonArray();
    }

    static byte[] pdf(JsonObject report) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Originality Desk - evidence report");
        lines.add("Evidence is not a misconduct determination.");
                lines.add("PDF font limitation: unsupported glyphs are rendered as '?'; use JSON/HTML for original Unicode.");
        for (String key : List.of("title", "engineVersion", "profile", "wordCount", "exactOverlap", "nearOverlap", "totalOverlap", "semanticStatus", "aiWritingStatus", "limitations", "citations"))
            wrap(lines, key + ": " + text(key.equals("title") ? report : evidence(report), key));
        for (JsonElement value : matches(report)) {
            JsonObject m = value.getAsJsonObject();
            wrap(lines, "Source: " + text(m, "sourceTitle") + " [" + text(m, "method") + "]");
            String resolver = text(m, "resolverUrl");
            if (resolver.isBlank()) resolver = text(m, "persistentResolverUrl");
            if (!resolver.isBlank()) {
                wrap(lines, "Publisher Record: " + resolver);
            }
            wrap(lines, "Submitted: " + text(m, "submittedPassage"));
            wrap(lines, "Source passage: " + text(m, "sourcePassage"));
            wrap(lines, "Citation: " + text(m, "citationObservation"));
        }
        lines.add("Submitted document:");
        wrap(lines, text(report, "documentText"));
        if (lines.size() > 20_000) throw new ApiException(413, "REPORT_TOO_LARGE", "Report exceeds PDF page limit");
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int start = 0; start < lines.size(); start += 52) {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText(); content.setFont(font, 10); content.setLeading(14);
                    content.newLineAtOffset(40, 800);
                    for (int i = start; i < Math.min(start + 52, lines.size()); i++) {
                        StringBuilder supported = new StringBuilder();
                        for (int p = 0; p < lines.get(i).length(); p++) {
                            String ch = String.valueOf(lines.get(i).charAt(p));
                            try { font.encode(ch); supported.append(ch); }
                            catch (IllegalArgumentException e) { supported.append('?'); }
                        }
                        content.showText(supported.toString()); content.newLine();
                    }
                    content.endText();
                }
            }
            pdf.save(out); return out.toByteArray();
        }
    }
    private static void wrap(List<String> lines, String text) {
        for (String line : text.replace('\r', '\n').split("\n", -1)) {
            String clean = line.replaceAll("[\\p{Cntrl}]", " ");
            if (clean.isEmpty()) lines.add("");
            for (int p = 0; p < clean.length(); p += 95) lines.add(clean.substring(p, Math.min(p + 95, clean.length())));
        }
    }
}

package com.example.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 简易字符级搜索器：在每个内容块内匹配 oldText 并记录匹配的字符范围坐标。
 * 注意：该实现只在单个内容块内匹配，无法跨块匹配，能覆盖多数常见文本。
 */
public class TextSearcher extends PDFTextStripper {

    public static class Match {
        public int pageIndex;
        public float x;
        public float y;
        public float width;
        public float height;
        public org.apache.pdfbox.pdmodel.font.PDFont font;
        public float fontSizeInPt;
        public String matched; // 实际匹配到的文本
        public float tx; // text matrix translateX (user space)
        public float ty; // text matrix translateY (user space, baseline)
        public float endX; // 匹配末尾的 x 位置（右侧）
        public String rest; // 同一内容块内，匹配后的剩余文本
    }

    private final String needle;
    private final boolean ignoreCase;
    private final List<Match> matches = new ArrayList<>();
    private final List<LineInfo> lines = new ArrayList<>();

    public TextSearcher(String needle, boolean ignoreCase) throws IOException {
        this.needle = needle == null ? "" : needle;
        this.ignoreCase = ignoreCase;
        // 确保逐页处理
        setSortByPosition(true);
    }

    public List<Match> find(PDDocument document) throws IOException {
        matches.clear();
        lines.clear();
        super.writeText(document, new java.io.OutputStreamWriter(java.io.OutputStream.nullOutputStream()));
        return new ArrayList<>(matches);
    }

    public List<LineInfo> getLines() {
        return new ArrayList<>(lines);
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        if (needle.isEmpty() || string == null || string.isEmpty() || textPositions == null || textPositions.isEmpty()) {
            // 仍然记录行，便于行级渲染
            if (string != null && textPositions != null && !textPositions.isEmpty()) {
                recordLine(string, textPositions);
            }
            return;
        }

        String hay = string;
        String ndl = needle;
        if (ignoreCase) {
            hay = hay.toLowerCase();
            ndl = ndl.toLowerCase();
        }

        int n = ndl.length();
        for (int i = 0; i + n <= hay.length(); i++) {
            if (hay.regionMatches(i, ndl, 0, n)) {
                // 收集 i..i+n-1 范围内的 TextPosition
                int endIdx = Math.min(i + n - 1, textPositions.size() - 1);
                TextPosition start = safeGet(textPositions, i);
                TextPosition end = safeGet(textPositions, endIdx);
                if (start == null || end == null) continue;

                float x0 = start.getXDirAdj();
                float y0 = start.getYDirAdj();
                // 宽度估算：以末字符的 (x + width) - x0
                float endRight = end.getXDirAdj() + end.getWidthDirAdj();
                float width = Math.max(0.1f, endRight - x0);
                float height = Math.max(start.getHeightDir(), end.getHeightDir());

                Match m = new Match();
                m.pageIndex = getCurrentPageNo() - 1; // 0-based
                m.x = x0;
                m.y = y0;
                m.width = width;
                m.height = height;
                m.font = start.getFont();
                m.fontSizeInPt = start.getFontSizeInPt();
                m.matched = string.substring(i, Math.min(i + n, string.length()));
                m.tx = start.getTextMatrix().getTranslateX();
                m.ty = start.getTextMatrix().getTranslateY();
                m.endX = endRight;
                if (i + n <= string.length()) {
                    m.rest = string.substring(i + n);
                } else {
                    m.rest = "";
                }
                matches.add(m);
            }
        }

        // 记录当前内容块为一行（粗略）
        recordLine(string, textPositions);
    }

    private TextPosition safeGet(List<TextPosition> list, int index) {
        if (index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    public static class LineInfo {
        public int pageIndex;
        public String text;
        public float xStart;
        public float yBaseline;
        public float width;
        public float height;
        public org.apache.pdfbox.pdmodel.font.PDFont font;
        public float fontSizeInPt;
    }

    private void recordLine(String string, List<TextPosition> textPositions) {
        if (string == null || textPositions == null || textPositions.isEmpty()) return;
        TextPosition first = textPositions.get(0);
        TextPosition last = textPositions.get(textPositions.size() - 1);
        float minX = first.getXDirAdj();
        for (TextPosition tp : textPositions) {
            if (tp.getXDirAdj() < minX) minX = tp.getXDirAdj();
        }
        float endRight = last.getXDirAdj() + last.getWidthDirAdj();
        float width = Math.max(0.1f, endRight - minX);
        float height = 0f;
        for (TextPosition tp : textPositions) {
            height = Math.max(height, tp.getHeightDir());
        }
        LineInfo line = new LineInfo();
        line.pageIndex = getCurrentPageNo() - 1;
        line.text = string;
        line.xStart = first.getTextMatrix().getTranslateX();
        line.yBaseline = first.getTextMatrix().getTranslateY();
        line.width = width;
        line.height = height;
        line.font = first.getFont();
        line.fontSizeInPt = first.getFontSizeInPt();
        lines.add(line);
    }
}



package com.example.pdfdemo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 收集 PDF 中文本块（按 writeString 提供的块）在页面上的大致包围框。
 * 返回的坐标单位为 PDF 用户空间（点，72dpi），原点在页面左下角：
 *  - x: 左边界
 *  - yTop: 上边界（用于前端从顶端定位）
 *  - width/height: 宽高
 */
public class TextBoxCollector extends PDFTextStripper {

    public enum Mode { LINE, WORD }

    public static class Box {
        public int pageIndex;     // 0-based
        public float x;           // 左
        public float yTop;        // 上（与 CSS top 对齐时：topPx = (pageHeight - yTop) * scale）
        public float width;
        public float height;
        public String text;       // 文本内容
        public float pageWidth;   // 页面宽（用户空间）
        public float pageHeight;  // 页面高（用户空间）
    }

    private final List<Box> boxes = new ArrayList<>();
    private float currentPageWidth;
    private float currentPageHeight;

    // 行聚合：按同页且基线 y 相近（容差）聚为一行
    private static class Group {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float baselineY; // 代表值（行基线）
        float maxAscent = 0f;   // 该行最大上升距
        float maxDescent = 0f;  // 该行最大下降距
        StringBuilder text = new StringBuilder();
        Group(float baselineY) { this.baselineY = baselineY; }
        void absorb(float minX, float maxX, float ascent, float descent) {
            if (minX < this.minX) this.minX = minX;
            if (maxX > this.maxX) this.maxX = maxX;
            if (ascent > this.maxAscent) this.maxAscent = ascent;
            if (descent > this.maxDescent) this.maxDescent = descent;
        }
    }
    private final List<Group> currentGroups = new ArrayList<>();

    private final Mode mode;

    public TextBoxCollector() throws IOException { this(Mode.LINE); }
    public TextBoxCollector(Mode mode) throws IOException {
        setSortByPosition(true);
        this.mode = mode == null ? Mode.LINE : mode;
    }

    public List<Box> collect(PDDocument document) throws IOException {
        boxes.clear();
        currentGroups.clear();
        super.writeText(document, new java.io.OutputStreamWriter(java.io.OutputStream.nullOutputStream()));
        return new ArrayList<>(boxes);
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        super.startPage(page);
        PDRectangle mediaBox = page.getMediaBox();
        currentPageWidth = mediaBox.getWidth();
        currentPageHeight = mediaBox.getHeight();
        currentGroups.clear();
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        if (mode == Mode.WORD) { // 词级模式不做行聚合输出
            super.endPage(page);
            return;
        }
        // 将聚合后的行转为 Box（使用 baseline + ascent/descent 计算上下边界）
        for (Group g : currentGroups) {
            if (g.maxX <= g.minX) continue;
            Box b = new Box();
            b.pageIndex = getCurrentPageNo() - 1;
            b.x = g.minX;
            float topY = g.baselineY + g.maxAscent;
            float bottomY = g.baselineY - g.maxDescent;
            b.yTop = topY;
            b.width = Math.max(0.1f, g.maxX - g.minX);
            b.height = Math.max(0.1f, topY - bottomY);
            b.text = g.text.toString();
            b.pageWidth = currentPageWidth;
            b.pageHeight = currentPageHeight;
            boxes.add(b);
        }
        currentGroups.clear();
        super.endPage(page);
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        if (textPositions == null || textPositions.isEmpty()) return;

        // 以块内所有字符求水平范围，并计算该块的 ascent/descent 以便行级聚合
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        for (TextPosition tp : textPositions) {
            float xLeft = tp.getXDirAdj();
            float xRight = xLeft + tp.getWidthDirAdj();
            if (xLeft < minX) minX = xLeft;
            if (xRight > maxX) maxX = xRight;
        }

        TextPosition first = textPositions.get(0);
        float baselineY = first.getTextMatrix().getTranslateY();
        // 计算该块的 ascent/descent（取最大值）
        float blockAscent = 0f;
        float blockDescent = 0f;
        for (TextPosition tp : textPositions) {
            var font = tp.getFont();
            float fs = tp.getFontSizeInPt();
            float asc = tp.getHeightDir(); // 兜底
            float desc = 0.2f * fs;        // 兜底
            if (font != null && font.getFontDescriptor() != null) {
                asc = Math.max(asc, font.getFontDescriptor().getAscent() / 1000f * fs);
                desc = Math.max(desc, Math.abs(font.getFontDescriptor().getDescent() / 1000f * fs));
            }
            if (asc > blockAscent) blockAscent = asc;
            if (desc > blockDescent) blockDescent = desc;
        }

        if (mode == Mode.LINE) {
            // 查找同页、基线接近的行进行聚合（容差 1.0f 点）
            final float epsilon = 1.0f;
            Group target = null;
            for (Group g : currentGroups) {
                if (Math.abs(g.baselineY - baselineY) <= epsilon) { target = g; break; }
            }
            if (target == null) {
                target = new Group(baselineY);
                currentGroups.add(target);
            }
            target.absorb(minX, maxX, blockAscent, blockDescent);
            if (string != null) target.text.append(string);
            // 行级：在 endPage 统一输出
            return;
        }

        // WORD 模式：按字间距阈值与空白共同分词，生成更稳定的词包围框
        int n = Math.min(string.length(), textPositions.size());
        if (n <= 0) return;
        // 估计空间阈值（优先字体的 spaceWidth，其次 0.5*fontSize）
        TextPosition f0 = textPositions.get(0);
        float fs0 = f0.getFontSizeInPt();
        float spaceWidth = 0.5f * fs0;
        try {
            if (f0.getFont() != null) {
                float sw = f0.getFont().getSpaceWidth();
                if (sw > 0) spaceWidth = Math.max(spaceWidth, sw / 1000f * fs0);
            }
        } catch (Exception ignored) {}
        final float gapThreshold = Math.max(0.5f, spaceWidth * 0.6f);

        int runStart = 0;
        for (int i = 1; i < n; i++) {
            TextPosition prev = textPositions.get(i - 1);
            TextPosition curr = textPositions.get(i);
            char ch = string.charAt(i);
            boolean isSpace = Character.isWhitespace(ch);
            float prevRight = prev.getXDirAdj() + prev.getWidthDirAdj();
            float currLeft = curr.getXDirAdj();
            boolean bigGap = (currLeft - prevRight) > gapThreshold;
            boolean endRun = isSpace || bigGap || i == n - 1;
            if (endRun) {
                int runEnd = (i == n - 1 && !isSpace && !bigGap) ? i : i - 1;
                if (runEnd >= runStart) {
                    float wMinX = Float.MAX_VALUE, wMaxX = -Float.MAX_VALUE;
                    float wTop = -Float.MAX_VALUE, wBottom = Float.MAX_VALUE;
                    for (int k = runStart; k <= runEnd && k < textPositions.size(); k++) {
                        TextPosition tp = textPositions.get(k);
                        float xL = tp.getXDirAdj();
                        float xR = xL + tp.getWidthDirAdj();
                        if (xL < wMinX) wMinX = xL;
                        if (xR > wMaxX) wMaxX = xR;
                        float base = tp.getTextMatrix().getTranslateY();
                        float fs = tp.getFontSizeInPt();
                        float asc = tp.getHeightDir();
                        float desc = 0.2f * fs;
                        if (tp.getFont() != null && tp.getFont().getFontDescriptor() != null) {
                            asc = Math.max(asc, tp.getFont().getFontDescriptor().getAscent() / 1000f * fs);
                            desc = Math.max(desc, Math.abs(tp.getFont().getFontDescriptor().getDescent() / 1000f * fs));
                        }
                        float top = base + asc;
                        float bottom = base - desc;
                        if (top > wTop) wTop = top;
                        if (bottom < wBottom) wBottom = bottom;
                    }
                    if (wMaxX > wMinX && wTop > wBottom) {
                        Box b = new Box();
                        b.pageIndex = getCurrentPageNo() - 1;
                        b.x = wMinX;
                        b.yTop = wTop;
                        b.width = Math.max(0.1f, wMaxX - wMinX);
                        b.height = Math.max(0.1f, wTop - wBottom);
                        b.text = string.substring(runStart, Math.min(runEnd + 1, string.length()));
                        b.pageWidth = currentPageWidth;
                        b.pageHeight = currentPageHeight;
                        boxes.add(b);
                    }
                }
                runStart = i + 1;
            }
        }
    }

    // 保留扩展空间：若后续需要更复杂行高估计，可在此实现
}



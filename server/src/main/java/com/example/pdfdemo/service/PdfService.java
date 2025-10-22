package com.example.pdfdemo.service;

import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfService {

    private static final String EXAMPLE_FILE_NAME = "example.pdf";
    private static final float DEFAULT_FONT_SIZE = 12f;
    private static final float MARGIN = 50f;

    private Path getStorageDir() {
        return Paths.get(System.getProperty("user.dir"), "data");
    }

    private Path getExamplePdfPath() {
        return getStorageDir().resolve(EXAMPLE_FILE_NAME);
    }

    @PostConstruct
    public void ensureSamplePdf() throws IOException {
        Path dir = getStorageDir();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path example = getExamplePdfPath();
        if (!Files.exists(example)) {
            createSamplePdf(example);
        }
    }

    private void createSamplePdf(Path target) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(PDType1Font.HELVETICA, 16);
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN - 20);
                contentStream.showText("Hello world - PDFBox sample");
                contentStream.endText();

                contentStream.setFont(PDType1Font.HELVETICA, DEFAULT_FONT_SIZE);
                contentStream.beginText();
                contentStream.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN - 60);
                contentStream.setLeading(16);
                contentStream.showText("This is a demo PDF used for text replace.");
                contentStream.newLine();
                contentStream.showText("Try replacing 'world' with another word.");
                contentStream.newLine();
                contentStream.showText("Note: This sample uses simple reflow after replacement.");
                contentStream.endText();
            }

            document.save(target.toFile());
        }
    }

    public byte[] getSamplePdf() throws IOException {
        return Files.readAllBytes(getExamplePdfPath());
    }

    public byte[] editPdfReplace(String oldText, String newText) throws IOException {
        // 读取原文
        String allText;
        try (PDDocument document = PDDocument.load(getExamplePdfPath().toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            allText = stripper.getText(document);
        }

        if (oldText != null && newText != null && !oldText.isEmpty()) {
            allText = allText.replace(oldText, newText);
        }

        // 重新写入到新 PDF（简单重排，单页渲染）
        try (PDDocument newDoc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            newDoc.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(newDoc, page)) {
                content.setFont(PDType1Font.HELVETICA, DEFAULT_FONT_SIZE);
                content.beginText();
                float startX = MARGIN;
                float startY = page.getMediaBox().getHeight() - MARGIN;
                content.newLineAtOffset(startX, startY);
                content.setLeading(16);

                List<String> lines = wrapText(allText, PDType1Font.HELVETICA, DEFAULT_FONT_SIZE,
                        page.getMediaBox().getWidth() - 2 * MARGIN);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                newDoc.save(baos);
                return baos.toByteArray();
            }
        }
    }

    public byte[] editPdfReplaceInplace(String oldText, String newText, boolean ignoreCase) throws IOException {
        if (oldText == null || oldText.isEmpty()) {
            return getSamplePdf();
        }

        try (PDDocument document = PDDocument.load(getExamplePdfPath().toFile())) {
            TextSearcher searcher = new TextSearcher(oldText, ignoreCase);
            List<TextSearcher.Match> positions = searcher.find(document);

            for (TextSearcher.Match m : positions) {
                if (m.pageIndex < 0 || m.pageIndex >= document.getNumberOfPages()) continue;
                var page = document.getPage(m.pageIndex);
                try (PDPageContentStream cs = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    // 使用文字矩阵平移（基线坐标）
                    float drawX = m.tx;
                    float drawBaselineY = m.ty;

                    // 计算覆盖矩形：用字体的 ascent/descent 更稳妥
                    var font = m.font != null ? m.font : PDType1Font.HELVETICA;
                    float fontSize = m.fontSizeInPt > 0 ? m.fontSizeInPt : DEFAULT_FONT_SIZE;
                    float ascent = 0f;
                    float descent = 0f;
                    if (font.getFontDescriptor() != null) {
                        ascent = Math.max(0f, font.getFontDescriptor().getAscent() / 1000f * fontSize);
                        descent = Math.abs(font.getFontDescriptor().getDescent() / 1000f * fontSize);
                    } else if (font.getBoundingBox() != null) {
                        ascent = Math.max(0f, font.getBoundingBox().getUpperRightY() / 1000f * fontSize);
                        descent = Math.abs(font.getBoundingBox().getLowerLeftY() / 1000f * fontSize);
                    } else {
                        ascent = m.height;
                        descent = 0.2f * fontSize;
                    }
                    float coverHeight = Math.max(m.height, ascent + descent) + 0.1f * fontSize;
                    // 仅覆盖原匹配区域（避免遮住后续文字）
                    float pad = 0.05f * fontSize;
                    float availableWidth = Math.max(0.1f * fontSize, m.width);
                    float rectY = drawBaselineY - descent - pad;
                    float coverWidth = availableWidth + 2 * pad;

                    // 遮盖原文字区域（白底）
                    cs.addRect(drawX - 0.05f * fontSize, rectY, coverWidth, coverHeight);
                    cs.setNonStrokingColor(java.awt.Color.WHITE);
                    cs.fill();

                    // 写入新文字：保持字号，稍后右移后续文本
                    cs.beginText();
                    cs.setNonStrokingColor(java.awt.Color.BLACK);
                    float newTextWidth;
                    try {
                        newTextWidth = Math.abs(font.getStringWidth(newText)) / 1000f * fontSize;
                    } catch (Exception e) {
                        newTextWidth = availableWidth;
                    }
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(drawX, drawBaselineY);
                    cs.showText(newText);
                    cs.endText();

                    // 右移并重绘“同一内容块内”的后续文本
                    float delta = newTextWidth - availableWidth; // >0 说明更长
                    if (delta > 0 && m.rest != null && !m.rest.isEmpty()) {
                        // 覆盖后续原文本区域
                        float restWidth = 0f;
                        try {
                            restWidth = Math.abs(font.getStringWidth(m.rest)) / 1000f * fontSize;
                        } catch (Exception ignored) {}
                        float restX = m.endX;
                        float restRectY = drawBaselineY - descent - pad;
                        cs.addRect(restX - pad, restRectY, restWidth + 2 * pad, coverHeight);
                        cs.setNonStrokingColor(java.awt.Color.WHITE);
                        cs.fill();

                        // 在新位置绘制 rest（整体右移 delta）
                        cs.beginText();
                        cs.setNonStrokingColor(java.awt.Color.BLACK);
                        cs.setFont(font, fontSize);
                        cs.newLineAtOffset(restX + delta, drawBaselineY);
                        cs.showText(m.rest);
                        cs.endText();
                    }
                }
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                document.save(baos);
                return baos.toByteArray();
            }
        }
    }

    public byte[] editWholeLine(String oldText, String newText, boolean ignoreCase) throws IOException {
        if (oldText == null || oldText.isEmpty()) {
            return getSamplePdf();
        }

        try (PDDocument document = PDDocument.load(getExamplePdfPath().toFile())) {
            TextSearcher searcher = new TextSearcher(oldText, ignoreCase);
            List<TextSearcher.Match> matches = searcher.find(document);
            List<TextSearcher.LineInfo> lines = searcher.getLines();

            for (TextSearcher.Match m : matches) {
                // 找到包含该 match 的行（同页且 y 基线接近）
                TextSearcher.LineInfo line = null;
                for (TextSearcher.LineInfo li : lines) {
                    if (li.pageIndex != m.pageIndex) continue;
                    if (Math.abs(li.yBaseline - m.ty) < Math.max(0.5f, m.height)) {
                        line = li;
                        break;
                    }
                }
                if (line == null) continue;

                var page = document.getPage(m.pageIndex);
                try (PDPageContentStream cs = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    // 字体信息
                    var font = line.font != null ? line.font : PDType1Font.HELVETICA;
                    float fontSize = line.fontSizeInPt > 0 ? line.fontSizeInPt : DEFAULT_FONT_SIZE;

                    // 计算覆盖整个行的矩形
                    float ascent = 0f, descent = 0f;
                    if (font.getFontDescriptor() != null) {
                        ascent = Math.max(0f, font.getFontDescriptor().getAscent() / 1000f * fontSize);
                        descent = Math.abs(font.getFontDescriptor().getDescent() / 1000f * fontSize);
                    }
                    float pad = 0.08f * fontSize;
                    float rectY = line.yBaseline - descent - pad;
                    float rectW = line.width + 2 * pad;
                    float rectH = Math.max(line.height, ascent + descent) + 2 * pad;

                    // 1) 覆盖整行
                    cs.addRect(line.xStart - pad, rectY, rectW, rectH);
                    cs.setNonStrokingColor(java.awt.Color.WHITE);
                    cs.fill();

                    // 2) 生成新行文本：用字符串替换（忽略/不忽略大小写）
                    String source = line.text;
                    String replaced;
                    if (ignoreCase) {
                        // 简易忽略大小写替换：逐次查找
                        String lowerSrc = source.toLowerCase();
                        String lowerOld = oldText.toLowerCase();
                        StringBuilder sb = new StringBuilder();
                        int idx = 0; int pos;
                        while ((pos = lowerSrc.indexOf(lowerOld, idx)) >= 0) {
                            sb.append(source, idx, pos).append(newText);
                            idx = pos + oldText.length();
                        }
                        sb.append(source.substring(idx));
                        replaced = sb.toString();
                    } else {
                        replaced = source.replace(oldText, newText);
                    }

                    // 3) 将整行作为一个文本块重新绘制（不移动其它行）
                    cs.beginText();
                    cs.setNonStrokingColor(java.awt.Color.BLACK);
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(line.xStart, line.yBaseline);
                    cs.showText(replaced);
                    cs.endText();
                }
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                document.save(baos);
                return baos.toByteArray();
            }
        }
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> wrapped = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return wrapped;
        }

        String[] paragraphs = text.split("\r?\n");
        for (String para : paragraphs) {
            if (para.isEmpty()) {
                wrapped.add("");
                continue;
            }

            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                float width = font.getStringWidth(candidate) / 1000 * fontSize;
                if (width <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (line.length() > 0) {
                        wrapped.add(line.toString());
                        line.setLength(0);
                        line.append(word);
                    } else {
                        // 单词本身超长，直接硬切
                        wrapped.add(candidate);
                        line.setLength(0);
                    }
                }
            }
            if (line.length() > 0) {
                wrapped.add(line.toString());
            }
        }
        return wrapped;
    }
}



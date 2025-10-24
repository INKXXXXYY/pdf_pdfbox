package com.example.pdfdemo.controller;

import com.example.pdfdemo.service.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping("/sample")
    public ResponseEntity<byte[]> getSample() throws IOException {
        byte[] data = pdfService.getSamplePdf();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=example.pdf")
                .body(data);
    }

    @PostMapping("/edit")
    public ResponseEntity<byte[]> edit(@RequestBody Map<String, String> body) throws IOException {
        String oldText = body.getOrDefault("oldText", "");
        String newText = body.getOrDefault("newText", "");
        byte[] data = pdfService.editPdfReplace(oldText, newText);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=modified.pdf")
                .body(data);
    }

    @PostMapping("/edit-inplace")
    public ResponseEntity<byte[]> editInplace(@RequestBody Map<String, String> body) throws IOException {
        String oldText = body.getOrDefault("oldText", "");
        String newText = body.getOrDefault("newText", "");
        boolean ignoreCase = Boolean.parseBoolean(body.getOrDefault("ignoreCase", "false"));
        byte[] data = pdfService.editPdfReplaceInplace(oldText, newText, ignoreCase);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=modified_inplace.pdf")
                .body(data);
    }

    @PostMapping("/edit-line")
    public ResponseEntity<byte[]> editWholeLine(@RequestBody Map<String, String> body) throws IOException {
        String oldText = body.getOrDefault("oldText", "");
        String newText = body.getOrDefault("newText", "");
        boolean ignoreCase = Boolean.parseBoolean(body.getOrDefault("ignoreCase", "false"));
        Integer pageIndex = null;
        if (body.containsKey("pageIndex")) {
            try { pageIndex = Integer.parseInt(body.get("pageIndex")); } catch (Exception ignored) {}
        }
        String lineText = body.getOrDefault("lineText", null);

        byte[] data = pdfService.editWholeLine(oldText, newText, ignoreCase, pageIndex, lineText);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=modified_line.pdf")
                .body(data);
    }

    @GetMapping(value = "/text-boxes", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<com.example.pdfdemo.service.TextBoxCollector.Box> getTextBoxes(@RequestParam(value = "mode", required = false) String mode) throws IOException {
        return pdfService.collectTextBoxes(mode);
    }

    @GetMapping(value = "/annotated")
    public ResponseEntity<byte[]> getAnnotated(@RequestParam(value = "mode", required = false) String mode) throws IOException {
        byte[] data = pdfService.renderAnnotatedTextBoxes(mode);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=annotated.pdf")
                .body(data);
    }
}



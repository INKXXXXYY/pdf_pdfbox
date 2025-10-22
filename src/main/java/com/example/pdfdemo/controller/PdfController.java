package com.example.pdfdemo.controller;

import com.example.pdfdemo.service.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
        byte[] data = pdfService.editWholeLine(oldText, newText, ignoreCase);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=modified_line.pdf")
                .body(data);
    }
}



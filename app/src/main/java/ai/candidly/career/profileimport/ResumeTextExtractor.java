package ai.candidly.career.profileimport;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Plain-code text extraction from an uploaded resume - no AI involved, this is a
 * deterministic format conversion. Only PDF and DOCX are supported (the only formats the
 * upload UI advertises); anything else is rejected with a 400 rather than silently
 * failing later in {@link ProfileExtractionService}.
 */
@Component
public class ResumeTextExtractor {

    public String extract(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try (InputStream in = file.getInputStream()) {
            if (filename.endsWith(".pdf")) {
                return extractPdf(in);
            }
            if (filename.endsWith(".docx")) {
                return extractDocx(in);
            }
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Unsupported resume format - only PDF and DOCX are supported");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Could not read the uploaded file");
        }
    }

    private String extractPdf(InputStream in) throws IOException {
        try (var document = Loader.loadPDF(in.readAllBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(InputStream in) throws IOException {
        try (XWPFDocument document = new XWPFDocument(in);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}

package ai.candidly.career.profileimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Builds tiny fixture documents in-memory (rather than checking in binary files) and
 * round-trips them through the real PDFBox/POI extraction code. */
class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractsTextFromPdf() throws IOException {
        byte[] pdfBytes = buildPdf("Jane Doe - Senior Engineer");
        var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);

        assertThat(extractor.extract(file)).contains("Jane Doe - Senior Engineer");
    }

    @Test
    void extractsTextFromDocx() throws IOException {
        byte[] docxBytes = buildDocx("Jane Doe - Senior Engineer");
        var file = new MockMultipartFile("file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        assertThat(extractor.extract(file)).contains("Jane Doe - Senior Engineer");
    }

    @Test
    void rejectsUnsupportedFormat() {
        var file = new MockMultipartFile("file", "resume.txt", "text/plain", "plain text".getBytes());

        assertThatThrownBy(() -> extractor.extract(file)).isInstanceOf(ResponseStatusException.class);
    }

    private byte[] buildPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }
}

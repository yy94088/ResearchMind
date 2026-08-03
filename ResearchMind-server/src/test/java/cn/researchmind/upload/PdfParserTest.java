package cn.researchmind.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfParserTest {

    private final PdfParser parser = new PdfParser();

    @Test
    void shouldExtractMetadataTextAndPageCount() throws Exception {
        byte[] pdf = createPdf();

        ParsedPdf result = parser.parse(pdf, "fallback-title.pdf");

        assertThat(result.title()).isEqualTo("ResearchMind PDF Pipeline");
        assertThat(result.authors()).containsExactly("Alice", "Bob");
        assertThat(result.institutions()).containsExactly("ResearchMind University");
        assertThat(result.keywords()).containsExactly("PDF", "Research");
        assertThat(result.abstractText()).contains("real PDF parsing pipeline");
        assertThat(result.doi()).isEqualTo("10.2026/researchmind.001");
        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.pages()).isEqualTo(1);
        assertThat(parser.extractText(pdf, 20, 10_000))
                .contains("ResearchMind PDF Pipeline")
                .contains("10.2026/researchmind.001");
    }

    private byte[] createPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = new PDDocumentInformation();
            information.setTitle("ResearchMind PDF Pipeline");
            information.setAuthor("Alice; Bob");
            information.setKeywords("PDF, Research");
            information.setCustomMetadataValue("Institution", "ResearchMind University");
            Calendar creationDate = Calendar.getInstance();
            creationDate.set(Calendar.YEAR, 2026);
            information.setCreationDate(creationDate);
            document.setDocumentInformation(information);

            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 740);
                content.showText("ResearchMind PDF Pipeline");
                content.newLineAtOffset(0, -24);
                content.showText("Abstract: This document validates the real PDF parsing pipeline ");
                content.newLineAtOffset(0, -16);
                content.showText("and extracts structured metadata for research papers.");
                content.newLineAtOffset(0, -24);
                content.showText("Keywords: PDF, Research");
                content.newLineAtOffset(0, -24);
                content.showText("DOI: 10.2026/researchmind.001");
                content.endText();
            }

            document.save(output);
            return output.toByteArray();
        }
    }
}

package de.tum.in.www1.artemis.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.service.util.Tuple;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;

@Service
public class UnitProcessingService {

    private final Logger log = LoggerFactory.getLogger(FileService.class);

    public Optional<List<LectureUnitSplitDTO>> splitPdfFile(MultipartFile file, String lectureId) throws IOException {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<LectureUnitSplitDTO> units = new ArrayList<>();

        PDDocument document = PDDocument.load(file.getBytes());
        // PDDocument documentRemovedBreakSlides = removeBreakSlidesFromFile(document);
        List<PDDocument> unitsDocument = separateIntoUnits(document);

        for (PDDocument doc : unitsDocument) {
            doc.save(outputStream);
            LectureUnitSplitDTO newLectureUnit = new LectureUnitSplitDTO();
            newLectureUnit.setFile(outputStream.toByteArray());
            newLectureUnit.setAttachmentName(doc.getDocumentInformation().getTitle());
            newLectureUnit.setDescription(doc.getDocumentInformation().getSubject());
            newLectureUnit.setStartPage(doc.getDocumentInformation().getCustomMetadataValue("startPage"));
            newLectureUnit.setEndPage(doc.getDocumentInformation().getCustomMetadataValue("endPage"));
            units.add(newLectureUnit);
            outputStream.reset();
            doc.close();
        }
        outputStream.close();
        document.close();

        return Optional.of(units);
    }

    private String learningGoalsOfUnit(PDDocument unit) {
        // TODO: impl to extract learning goals
        // go through slides of unit
        // read each file and find slide with Learning goals text
        // extract the next lines after the Learning goals text
        // return a list of strings

        return "";
    }

    private String constructUnitName(PDDocument unit) {
        // TODO: impl to extract information of one unit and return the object
        // Read from slide
        // Store in variable line by line
        // align with unit number and line number
        // then get the line which is the same number with unit number
        // and return filename

        // File file = new File("File.pdf");
        // PDDocument document = PDDocument.load(file);
        // PDFTextStripper pdfStripper = new PDFTextStripper();
        // pdfStripper.setStartPage(1);
        // pdfStripper.setEndPage(1);
        //
        // //load all lines into a string
        // String pages = pdfStripper.getText(document);
        //
        // //split by detecting newline
        // String[] lines = pages.split("\r\n|\r|\n");
        //
        // int count=1; //Just to indicate line number
        // for(String temp:lines)
        // {
        // System.out.println(count+" "+temp);
        // count++;
        // }
        return "";
    }

    private PDDocument removeBreakSlidesFromFile(PDDocument document) throws IOException {
        Splitter pdfSplitter = new Splitter();
        PDFTextStripper pdfStripper = new PDFTextStripper();
        List<PDDocument> Pages = pdfSplitter.split(document);

        PDDocument newDocument = new PDDocument();

        for (PDDocument pd : Pages) {
            String slideText = pdfStripper.getText(pd);
            if (slideText.contains("Break")) {
                continue;
            }
            newDocument.addPage(pdfStripper.getCurrentPage());
        }
        return newDocument;
    }

    private List<PDDocument> separateIntoUnits(PDDocument document) throws IOException {
        // Store unit information like -> (outline: 1, startPage: 4, endPage: 22) for all units
        Map<Integer, Tuple<Integer, Integer>> outlineMap = new HashMap<>();

        Splitter pdfSplitter = new Splitter();
        PDFTextStripper pdfStripper = new PDFTextStripper();
        List<PDDocument> Pages = pdfSplitter.split(document);

        List<PDDocument> units = new ArrayList<>();
        Iterator<PDDocument> iterator = Pages.listIterator();

        int outlineCount = 0;
        int index = 1;
        while (iterator.hasNext()) {
            PDDocument pd = iterator.next();
            String slideText = pdfStripper.getText(pd);

            if (slideText.contains("Outline")) {
                outlineCount++;

                outlineMap.put(outlineCount, new Tuple<>((outlineCount == 1) ? 1 : index, document.getNumberOfPages()));
                if (outlineCount > 1) {
                    int previousOutlineCount = outlineCount - 1;
                    int previousStart = outlineMap.get(previousOutlineCount).x();
                    outlineMap.remove(previousOutlineCount);
                    outlineMap.put(previousOutlineCount, new Tuple<>(previousStart, index - 1));
                }
            }
            pd.close();
            index++;
        }

        outlineMap.forEach((k, v) -> System.out.println((k + ":" + v)));
        outlineMap.forEach((k, v) -> {
            PDDocument unit = new PDDocument();
            PDDocumentInformation pdDocumentInformation = new PDDocumentInformation();
            try {

                pdfSplitter.setStartPage(v.x());
                pdfSplitter.setEndPage(v.y());

                List<PDDocument> documentUnits = pdfSplitter.split(document);
                documentUnits.forEach(doc -> unit.addPage(doc.getPage(0)));

                pdDocumentInformation.setTitle("unitFileName:" + k);
                pdDocumentInformation.setSubject("Description test:" + k);
                pdDocumentInformation.setKeywords((v.x() + ":" + v.y()));
                pdDocumentInformation.setCustomMetadataValue("startPage", v.x().toString());
                pdDocumentInformation.setCustomMetadataValue("endPage", v.y().toString());
                unit.setDocumentInformation(pdDocumentInformation);
                units.add(unit);
            }
            catch (IOException e) {
                log.warn("Could not generate " + k + ". unit");
                throw new RuntimeException(e);
            }
        });

        return units;
    }
}

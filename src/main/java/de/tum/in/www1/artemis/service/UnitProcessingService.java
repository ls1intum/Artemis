package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.util.*;

import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
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

    public Optional<List<LectureUnitSplitDTO>> getSplitUnitData(MultipartFile file, String lectureId) throws IOException {

        List<LectureUnitSplitDTO> units = new ArrayList<>();

        PDDocument document = PDDocument.load(file.getBytes());
        Map<Integer, Tuple<String, Tuple<Integer, Integer>>> unitsDocumentMap = separateIntoUnits(document);

        unitsDocumentMap.forEach((k, v) -> {
            LectureUnitSplitDTO newLectureUnit = new LectureUnitSplitDTO();
            newLectureUnit.setUnitName(v.x());
            newLectureUnit.setStartPage(v.y().x().toString());
            newLectureUnit.setEndPage(v.y().y().toString());
            units.add(newLectureUnit);
        });

        document.close();
        return Optional.of(units);
    }

    private Map<Integer, Tuple<String, Tuple<Integer, Integer>>> separateIntoUnits(PDDocument document) throws IOException {
        Map<Integer, Tuple<String, Tuple<Integer, Integer>>> outlineMap = new HashMap<>();

        Splitter pdfSplitter = new Splitter();
        PDFTextStripper pdfStripper = new PDFTextStripper();
        List<PDDocument> Pages = pdfSplitter.split(document);

        Iterator<PDDocument> iterator = Pages.listIterator();

        int outlineCount = 0;
        int index = 1;
        while (iterator.hasNext()) {
            PDDocument pd = iterator.next();
            String slideText = pdfStripper.getText(pd);

            if (slideText.contains("Outline")) {
                outlineCount++;

                String[] lines = slideText.split("\r\n|\r|\n");

                outlineMap.put(outlineCount, new Tuple<>(lines[outlineCount + 1], new Tuple<>((outlineCount == 1) ? 1 : index, document.getNumberOfPages())));
                if (outlineCount > 1) {
                    int previousOutlineCount = outlineCount - 1;
                    int previousStart = outlineMap.get(previousOutlineCount).y().x();
                    outlineMap.remove(previousOutlineCount);
                    outlineMap.put(previousOutlineCount, new Tuple<>(lines[previousOutlineCount + 1], new Tuple<>(previousStart, index - 1)));
                }
            }
            index++;
        }
        return outlineMap;
    }
}

package de.tum.in.www1.artemis.service;

import java.io.*;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.service.util.Tuple;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;

@Service
public class LectureUnitProcessingService {

    /**
     * Split units from given file according to given split information.
     * @param lectureUnitSplitDTOs The split information
     * @param file The file (lecture slide) to be split
     * @return The prepared units to be saved
     */
    public List<LectureUnitDTO> splitUnits(List<LectureUnitSplitDTO> lectureUnitSplitDTOs, MultipartFile file) throws IOException {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<LectureUnitDTO> units = new ArrayList<>();
        Splitter pdfSplitter = new Splitter();
        PDDocument document = PDDocument.load(file.getBytes());
        // PDDocument breakRemoved = removeBreakSlidesFromFile(document);
        List<PDDocument> documentUnits;

        for (LectureUnitSplitDTO lectureUnit : lectureUnitSplitDTOs) {
            AttachmentUnit attachmentUnit = new AttachmentUnit();
            Attachment attachment = new Attachment();
            PDDocumentInformation pdDocumentInformation = new PDDocumentInformation();

            pdfSplitter.setStartPage(lectureUnit.startPage());
            pdfSplitter.setEndPage(lectureUnit.endPage());
            pdfSplitter.setSplitAtPage(lectureUnit.endPage());

            documentUnits = pdfSplitter.split(document);
            pdDocumentInformation.setTitle(lectureUnit.unitName());
            documentUnits.get(0).setDocumentInformation(pdDocumentInformation);
            documentUnits.get(0).save(outputStream);

            // setup attachmentUnit and attachment
            attachmentUnit.setDescription("");
            attachment.setName(lectureUnit.unitName());
            attachment.setAttachmentType(AttachmentType.FILE);
            attachment.setVersion(1);
            attachment.setReleaseDate(lectureUnit.releaseDate());
            attachment.setUploadDate(ZonedDateTime.now());

            // prepare file to be set from byte[] to MultipartFile by using CommonsMultipartFile
            String tempDirectory = System.getProperty("java.io.tmpdir");

            File tempFile = new File(tempDirectory, lectureUnit.unitName() + ".pdf");
            Files.write(tempFile.toPath(), outputStream.toByteArray());

            File outputFile = new File(tempFile.toString());
            FileItem fileItem = new DiskFileItem("mainUnitFile", Files.probeContentType(outputFile.toPath()), false, outputFile.getName(), (int) outputFile.length(),
                    outputFile.getParentFile());

            InputStream input = new FileInputStream(outputFile);
            OutputStream os = fileItem.getOutputStream();
            IOUtils.copy(input, os);
            MultipartFile multipartFile = new CommonsMultipartFile(fileItem);

            LectureUnitDTO lectureUnitsDTO = new LectureUnitDTO(attachmentUnit, attachment, multipartFile);

            units.add(lectureUnitsDTO);
            tempFile.deleteOnExit();
            documentUnits.get(0).close();
        }
        // breakRemoved.close();
        document.close();
        return units;
    }

    /**
     * Removes slides that contain Break word. (case sensitive)
     * @param document The document to remove the Break slides from
     * @return The new document with no Break slides
     */
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

    /**
     * Prepare information of split units for client
     * @param file The file (lecture slide) to be split
     * @return The prepared information of split units as list of LectureUnitSplitDTO
     */
    public List<LectureUnitSplitDTO> getSplitUnitData(MultipartFile file) throws IOException {

        List<LectureUnitSplitDTO> units = new ArrayList<>();
        PDDocument document = PDDocument.load(file.getBytes());
        // PDDocument breakRemoved = removeBreakSlidesFromFile(document);
        Map<Integer, Tuple<String, Tuple<Integer, Integer>>> unitsDocumentMap = separateIntoUnits(document);

        unitsDocumentMap.forEach((k, v) -> {
            LectureUnitSplitDTO newLectureUnit = new LectureUnitSplitDTO(v.x(), ZonedDateTime.now(), v.y().x(), v.y().y());
            units.add(newLectureUnit);
        });

        // breakRemoved.close();
        document.close();
        return units;
    }

    /**
     * This method prepares a map with information on how the slide
     * is going to be split. The map looks like the following:
     * Map<OutlineNumber, (UnitName, (StartPage, EndPage))>
     *
     * @param document The document (lecture slide) to be split
     * @return The prepared map
     */
    private Map<Integer, Tuple<String, Tuple<Integer, Integer>>> separateIntoUnits(PDDocument document) throws IOException {
        Map<Integer, Tuple<String, Tuple<Integer, Integer>>> outlineMap = new HashMap<>();
        outlineMap.put(0, new Tuple<>("One Unit", new Tuple<>(1, document.getNumberOfPages())));

        Splitter pdfSplitter = new Splitter();
        PDFTextStripper pdfStripper = new PDFTextStripper();
        List<PDDocument> Pages = pdfSplitter.split(document);

        ListIterator<PDDocument> iterator = Pages.listIterator();

        int outlineCount = 0;
        int index = 1;
        while (iterator.hasNext()) {
            PDDocument pdCurrent = iterator.next();
            String slideText = pdfStripper.getText(pdCurrent);

            if (slideText.contains("Outline")) {
                outlineCount++;

                String[] lines = slideText.split("\r\n|\r|\n");
                outlineMap.put(outlineCount, new Tuple<>(lines[outlineCount + 1], new Tuple<>((outlineCount == 1) ? 1 : index, document.getNumberOfPages())));
                if (outlineCount > 1) {

                    // get previous slide details (move 2 iterations back)
                    iterator.previous();
                    pdCurrent = iterator.previous();
                    String previousSlideText = pdfStripper.getText(pdCurrent);

                    // move 2 iteration forward
                    iterator.next();
                    iterator.next();

                    // update previous outline map endPage
                    int previousOutlineCount = outlineCount - 1;
                    int previousStart = outlineMap.get(previousOutlineCount).y().x();
                    outlineMap.remove(previousOutlineCount);
                    outlineMap.put(previousOutlineCount,
                            new Tuple<>(lines[previousOutlineCount + 1], new Tuple<>(previousStart, previousSlideText.contains("Break") ? index - 2 : index - 1)));
                }
            }
            index++;
        }
        return outlineMap;
    }
}

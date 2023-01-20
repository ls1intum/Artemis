package de.tum.in.www1.artemis.service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class LectureUnitProcessingService {

    private final Logger log = LoggerFactory.getLogger(LectureUnitProcessingService.class);

    /**
     * Split units from given file according to given split information.
     * @param lectureUnitInformationDTO The split information
     * @param file The file (lecture slide) to be split
     * @return The prepared units to be saved
     */
    public List<LectureUnitDTO> splitUnits(LectureUnitInformationDTO lectureUnitInformationDTO, MultipartFile file) throws IOException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = PDDocument.load(file.getBytes())) {
            List<LectureUnitDTO> units = new ArrayList<>();
            Splitter pdfSplitter = new Splitter();

            for (LectureUnitSplitDTO lectureUnit : lectureUnitInformationDTO.units) {
                AttachmentUnit attachmentUnit = new AttachmentUnit();
                Attachment attachment = new Attachment();
                PDDocumentInformation pdDocumentInformation = new PDDocumentInformation();

                pdfSplitter.setStartPage(lectureUnit.startPage());
                pdfSplitter.setEndPage(lectureUnit.endPage());
                pdfSplitter.setSplitAtPage(lectureUnit.endPage());

                List<PDDocument> documentUnits = pdfSplitter.split(document);
                pdDocumentInformation.setTitle(lectureUnit.unitName());
                if (lectureUnitInformationDTO.removeBreakSlides) {
                    removeBreakSlides(documentUnits.get(0));
                }
                documentUnits.get(0).setDocumentInformation(pdDocumentInformation);
                documentUnits.get(0).save(outputStream);

                // setup attachmentUnit and attachment
                attachmentUnit.setDescription("");
                attachment.setName(lectureUnit.unitName());
                attachment.setAttachmentType(AttachmentType.FILE);
                attachment.setReleaseDate(lectureUnit.releaseDate());
                attachment.setUploadDate(ZonedDateTime.now());

                MultipartFile multipartFile = convertByteArrayToMultipart(lectureUnit.unitName(), outputStream.toByteArray());

                LectureUnitDTO lectureUnitsDTO = new LectureUnitDTO(attachmentUnit, attachment, multipartFile);
                units.add(lectureUnitsDTO);
                documentUnits.get(0).close(); // make sure to close the document
            }
            document.close();
            return units;
        }
    }

    /**
     * Removes the break slides from the given document.
     * @param document         document to remove break slides from
     */
    private void removeBreakSlides(PDDocument document) {

        try {
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            Splitter pdfSplitter = new Splitter();
            List<PDDocument> pages = pdfSplitter.split(document);
            Iterator<PDDocument> iterator = pages.listIterator();

            int index = 0;
            while (iterator.hasNext()) {
                PDDocument currentPage = iterator.next();
                String slideText = pdfTextStripper.getText(currentPage);
                if (isBreakSlide(slideText)) {
                    document.removePage(index);
                    break;
                }
                currentPage.close(); // make sure to close the document
                index++;
            }
        }
        catch (IOException e) {
            log.error("Error while removing break slides from document", e);
            throw new InternalServerErrorException("Error while removing break slides from document");
        }
    }

    private boolean isBreakSlide(String slideText) {
        return slideText.contains("Break") || slideText.contains("Pause");
    }

    /**
     * Convert byte[] to MultipartFile by using CommonsMultipartFile
     * @param unitName         unit name to set file name
     * @param streamByteArray  byte array to save to the temp file
     * @return multipartFile
     */
    private MultipartFile convertByteArrayToMultipart(String unitName, byte[] streamByteArray) throws IOException {
        Path tempPath = Path.of(FilePathService.getTempFilePath(), unitName + ".pdf");
        Files.write(tempPath, streamByteArray);
        File outputFile = Path.of(tempPath.toString()).toFile();
        FileItem fileItem = new DiskFileItem("mainUnitFile", Files.probeContentType(outputFile.toPath()), false, outputFile.getName(), (int) outputFile.length(),
                outputFile.getParentFile());

        try (InputStream input = new FileInputStream(outputFile); OutputStream fileItemOutputStream = fileItem.getOutputStream()) {
            IOUtils.copy(input, fileItemOutputStream);
        }
        return new CommonsMultipartFile(fileItem);
    }

    /**
     * Prepare information of split units for client
     * @param file The file (lecture slide) to be split
     * @return The prepared information of split units LectureUnitInformationDTO
     */
    public LectureUnitInformationDTO getSplitUnitData(MultipartFile file) {

        try {
            log.debug("Start preparing information of split units for the file {}", file);

            List<LectureUnitSplitDTO> units = new ArrayList<>();

            Outline unitsInformation = separateIntoUnits(file);
            Map<Integer, LectureUnitSplit> unitsDocumentMap = unitsInformation.splits;
            int numberOfPages = unitsInformation.totalPages;

            unitsDocumentMap.forEach((key, value) -> {
                LectureUnitSplitDTO newLectureUnit = new LectureUnitSplitDTO(value.unitName, ZonedDateTime.now(), value.startPage, value.endPage);
                units.add(newLectureUnit);
            });
            // return units information, maximum number of pages and by default remove break slides is false
            return new LectureUnitInformationDTO(units, numberOfPages, false);
        }
        catch (IOException e) {
            log.error("Error while preparing the map with information", e);
            throw new InternalServerErrorException("Could not prepare split information");
        }
    }

    /**
     * This method prepares a map with information on how the slide
     * is going to be split. The map looks like the following:
     * Map<OutlineNumber, (UnitName, StartPage, EndPage)>
     *
     * @param file The file (lecture pdf) to be split
     * @return The prepared map
     */
    private Outline separateIntoUnits(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            Map<Integer, LectureUnitSplit> outlineMap = new HashMap<>();
            Splitter pdfSplitter = new Splitter();
            PDFTextStripper pdfStripper = new PDFTextStripper();
            // split the document into single pages
            List<PDDocument> pages = pdfSplitter.split(document);
            int numberOfPages = document.getNumberOfPages();
            ListIterator<PDDocument> iterator = pages.listIterator();

            int outlineCount = 0;
            int index = 1;
            while (iterator.hasNext()) {
                PDDocument currentPage = iterator.next();
                String slideText = pdfStripper.getText(currentPage);

                if (isOutlineSlide(slideText)) {
                    outlineCount++;
                    String[] lines = slideText.split("\r\n|\r|\n");

                    // if it's the outline slide it will get the next bullet point as unit name.
                    String unitName = lines[outlineCount + 1].replaceAll("[^a-zA-Z0-9\\s()_-]", "").replaceFirst("^\\s*", "");
                    outlineMap.put(outlineCount, new LectureUnitSplit(unitName, outlineCount == 1 ? 1 : index, numberOfPages));

                    updatePreviousUnitEndPage(outlineCount, outlineMap, index);
                }
                currentPage.close(); // make sure to close the document
                index++;
            }
            document.close(); // make sure to close the document
            return new Outline(outlineMap, numberOfPages);
        }
    }

    /**
     * This method updates previous unit end page, and it's called if outline count is bigger then 1
     *
     * @param outlineCount Outline count is the number of slides that contain Outline
     * @param outlineMap   Outline map with unit information
     * @param index        index that shows current page
     */
    private void updatePreviousUnitEndPage(int outlineCount, @NotNull Map<Integer, LectureUnitSplit> outlineMap, int index) {
        if (outlineCount > 1) {
            int previousOutlineCount = outlineCount - 1;
            int previousStart = outlineMap.get(previousOutlineCount).startPage;
            String previousUnitName = outlineMap.get(previousOutlineCount).unitName;
            outlineMap.put(previousOutlineCount, new LectureUnitSplit(previousUnitName, previousStart, index - 1));
        }
    }

    private boolean isOutlineSlide(final String slideText) {
        return slideText.contains("Outline") || slideText.contains("Gliederung");
    }

    private record LectureUnitSplit(String unitName, int startPage, int endPage) {
    }

    /**
     * Map contains unit number as key and unit information as value
     */
    private record Outline(Map<Integer, LectureUnitSplit> splits, int totalPages) {
    }
}

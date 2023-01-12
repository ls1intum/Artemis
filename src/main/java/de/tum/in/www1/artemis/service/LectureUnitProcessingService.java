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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;
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

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = PDDocument.load(file.getBytes())) {
            List<LectureUnitDTO> units = new ArrayList<>();
            Splitter pdfSplitter = new Splitter();

            for (LectureUnitSplitDTO lectureUnit : lectureUnitSplitDTOs) {
                AttachmentUnit attachmentUnit = new AttachmentUnit();
                Attachment attachment = new Attachment();
                PDDocumentInformation pdDocumentInformation = new PDDocumentInformation();

                pdfSplitter.setStartPage(lectureUnit.startPage());
                pdfSplitter.setEndPage(lectureUnit.endPage());
                pdfSplitter.setSplitAtPage(lectureUnit.endPage());

                List<PDDocument> documentUnits = pdfSplitter.split(document);
                pdDocumentInformation.setTitle(lectureUnit.unitName());
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
                documentUnits.get(0).close();
            }

            outputStream.close();
            document.close();
            return units;
        }
    }

    /**
     * Prepare file to be set from byte[] to MultipartFile by using CommonsMultipartFile
     * @param unitName         unit name to set file name
     * @param streamByteArray  byte array to save to the temp file
     * @return multipartFile
     */
    private MultipartFile convertByteArrayToMultipart(String unitName, byte[] streamByteArray) throws IOException {
        Path tempPath = Path.of(FilePathService.getTempFilePath(), unitName + ".pdf");
        File tempFile = Path.of(tempPath.toString()).toFile();
        Files.write(tempPath, streamByteArray);
        File outputFile = Path.of(tempFile.toString()).toFile();
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
     * @return The prepared information of split units as list of LectureUnitSplitDTO
     */
    public LectureUnitInformationDTO getSplitUnitData(MultipartFile file) throws IOException {

        try (PDDocument document = PDDocument.load(file.getBytes())) {
            List<LectureUnitSplitDTO> units = new ArrayList<>();

            Outline unitsInformation = separateIntoUnits(document);
            Map<Integer, LectureUnitSplit> unitsDocumentMap = unitsInformation.splits;
            int numberOfPages = unitsInformation.totalPages;

            unitsDocumentMap.forEach((key, value) -> {
                LectureUnitSplitDTO newLectureUnit = new LectureUnitSplitDTO(value.unitName, ZonedDateTime.now(), value.startPage, value.endPage);
                units.add(newLectureUnit);
            });

            return new LectureUnitInformationDTO(units, numberOfPages);
        }

    }

    /**
     * This method prepares a map with information on how the slide
     * is going to be split. The map looks like the following:
     * Map<OutlineNumber, (UnitName, StartPage, EndPage)>
     *
     * @param document The document (lecture pdf) to be split
     * @return The prepared map
     */
    private Outline separateIntoUnits(PDDocument document) throws IOException {
        Map<Integer, LectureUnitSplit> outlineMap = new HashMap<>();

        Splitter pdfSplitter = new Splitter();
        PDFTextStripper pdfStripper = new PDFTextStripper();
        List<PDDocument> pages = pdfSplitter.split(document);

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
                outlineMap.put(outlineCount, new LectureUnitSplit(unitName, outlineCount == 1 ? 1 : index, document.getNumberOfPages()));

                if (outlineCount > 1) {
                    // two iterations back to access previous slide text
                    iterator.previous();
                    String previousPageText = pdfStripper.getText(iterator.previous());

                    updatePreviousUnitEndPage(outlineCount, outlineMap, index, previousPageText.contains("Break"));

                    // two iterations forward in order to be at current slide
                    iterator.next();
                    iterator.next();
                }
            }
            index++;
        }

        document.close();
        return new Outline(outlineMap, document.getNumberOfPages());
    }

    /**
     * This method updates previous unit end page, and it's called if outline count is bigger then 1
     *
     * @param outlineCount Outline count is the number of slides that contain Outline
     * @param outlineMap   Outline map with unit information
     * @param index        index that shows current page
     */
    private void updatePreviousUnitEndPage(int outlineCount, @NotNull Map<Integer, LectureUnitSplit> outlineMap, int index, boolean containsBreak) {
        int previousOutlineCount = outlineCount - 1;
        int previousStart = outlineMap.get(previousOutlineCount).startPage;
        String previousUnitName = outlineMap.get(previousOutlineCount).unitName;
        outlineMap.put(previousOutlineCount, new LectureUnitSplit(previousUnitName, previousStart, containsBreak ? index - 2 : index - 1));
    }

    private boolean isOutlineSlide(final String slideText) {
        return slideText.contains("Outline");
    }

    private record LectureUnitSplit(String unitName, int startPage, int endPage) {
    }

    private record Outline(Map<Integer, LectureUnitSplit> splits, int totalPages) {
    }
}

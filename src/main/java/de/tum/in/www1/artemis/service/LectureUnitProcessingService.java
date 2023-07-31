package de.tum.in.www1.artemis.service;

import java.io.*;
import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class LectureUnitProcessingService {

    private final Logger log = LoggerFactory.getLogger(LectureUnitProcessingService.class);

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final LectureRepository lectureRepository;

    private final AttachmentUnitService attachmentUnitService;

    public LectureUnitProcessingService(SlideSplitterService slideSplitterService, FileService fileService, LectureRepository lectureRepository,
            AttachmentUnitService attachmentUnitService) {
        this.fileService = fileService;
        this.slideSplitterService = slideSplitterService;
        this.lectureRepository = lectureRepository;
        this.attachmentUnitService = attachmentUnitService;
    }

    /**
     * Split units from given file according to given split information and saves them.
     *
     * @param lectureUnitInformationDTO The split information
     * @param file                      The file (lecture slide) to be split
     * @param lecture                   The lecture that the attachment unit belongs to
     * @return The prepared units to be saved
     */
    public List<AttachmentUnit> splitAndSaveUnits(LectureUnitInformationDTO lectureUnitInformationDTO, MultipartFile file, Lecture lecture) throws IOException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = PDDocument.load(file.getBytes())) {
            List<AttachmentUnit> units = new ArrayList<>();
            Splitter pdfSplitter = new Splitter();

            for (LectureUnitSplitDTO lectureUnit : lectureUnitInformationDTO.units()) {
                AttachmentUnit attachmentUnit = new AttachmentUnit();
                Attachment attachment = new Attachment();
                PDDocumentInformation pdDocumentInformation = new PDDocumentInformation();

                pdfSplitter.setStartPage(lectureUnit.startPage());
                pdfSplitter.setEndPage(lectureUnit.endPage());
                pdfSplitter.setSplitAtPage(lectureUnit.endPage());

                List<PDDocument> documentUnits = pdfSplitter.split(document);
                pdDocumentInformation.setTitle(lectureUnit.unitName());
                if (lectureUnitInformationDTO.removeBreakSlides() || lectureUnitInformationDTO.removeSolutionSlides()) {
                    removeBreakOrSolutionSlides(documentUnits.get(0), lectureUnitInformationDTO.removeBreakSlides(), lectureUnitInformationDTO.removeSolutionSlides());
                }
                documentUnits.get(0).setDocumentInformation(pdDocumentInformation);
                documentUnits.get(0).save(outputStream);

                // setup attachmentUnit and attachment
                attachmentUnit.setDescription("");
                attachment.setName(lectureUnit.unitName());
                attachment.setAttachmentType(AttachmentType.FILE);
                attachment.setReleaseDate(lectureUnit.releaseDate());
                attachment.setUploadDate(ZonedDateTime.now());

                MultipartFile multipartFile = fileService.convertByteArrayToMultipart(lectureUnit.unitName(), ".pdf", outputStream.toByteArray());
                AttachmentUnit savedAttachmentUnit = attachmentUnitService.createAttachmentUnit(attachmentUnit, attachment, lecture, multipartFile, true);
                slideSplitterService.splitAttachmentUnitIntoSingleSlides(documentUnits.get(0), savedAttachmentUnit, multipartFile.getOriginalFilename());
                documentUnits.get(0).close(); // make sure to close the document
                units.add(savedAttachmentUnit);
            }
            lectureRepository.save(lecture);
            document.close();
            return units;
        }
    }

    /**
     * Removes the break slides or solution slides from the given document.
     *
     * @param document document to remove break slides from
     */
    private void removeBreakOrSolutionSlides(PDDocument document, boolean removeBreakSlides, boolean removeSolutionSlides) {

        try {
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            Splitter pdfSplitter = new Splitter();
            List<PDDocument> pages = pdfSplitter.split(document);

            // Uses a decrementing loop (starting from the last index) to ensure that the
            // index values are adjusted correctly when removing pages.
            for (int index = pages.size() - 1; index >= 0; index--) {
                PDDocument currentPage = pages.get(index);
                String slideText = pdfTextStripper.getText(currentPage);

                if (isBreakSlide(slideText) && removeBreakSlides) {
                    document.removePage(index);
                }
                else if (isSolutionSlide(slideText) && removeSolutionSlides) {
                    document.removePage(index);
                }
                currentPage.close(); // make sure to close the document
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

    private boolean isSolutionSlide(String slideText) {
        return slideText.contains("Example solution");
    }

    /**
     * Prepare information of split units for client
     *
     * @param file The file (lecture slide) to be split
     * @return The prepared information of split units LectureUnitInformationDTO
     */
    public LectureUnitInformationDTO getSplitUnitData(MultipartFile file) {

        try {
            log.debug("Start preparing information of split units for the file {}", file);
            Outline unitsInformation = separateIntoUnits(file);
            Map<Integer, LectureUnitSplit> unitsDocumentMap = unitsInformation.splits;
            int numberOfPages = unitsInformation.totalPages;

            List<LectureUnitSplitDTO> units = unitsDocumentMap.values().stream()
                    .map(lectureUnitSplit -> new LectureUnitSplitDTO(lectureUnitSplit.unitName, ZonedDateTime.now(), lectureUnitSplit.startPage, lectureUnitSplit.endPage))
                    .toList();
            // return units information, maximum number of pages and by default remove break slides and remove solution slides are false
            return new LectureUnitInformationDTO(units, numberOfPages, false, false);
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

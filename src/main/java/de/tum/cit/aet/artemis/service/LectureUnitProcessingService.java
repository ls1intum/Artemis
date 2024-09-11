package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.domain.Attachment;
import de.tum.cit.aet.artemis.domain.Lecture;
import de.tum.cit.aet.artemis.domain.enumeration.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.LectureUnitSplitDTO;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorException;

@Profile(PROFILE_CORE)
@Service
public class LectureUnitProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitProcessingService.class);

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final LectureRepository lectureRepository;

    private final AttachmentUnitService attachmentUnitService;

    private final PDFTextStripper pdfTextStripper = new PDFTextStripper();

    // A pdf splitter that should be used to split a file into single pages
    private final Splitter pdfSinglePageSplitter = new Splitter();

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
     * @param fileBytes                 The byte content of the file (lecture slides) to be split
     * @param lecture                   The lecture that the attachment unit belongs to
     * @return The prepared units to be saved
     */
    public List<AttachmentUnit> splitAndSaveUnits(LectureUnitInformationDTO lectureUnitInformationDTO, byte[] fileBytes, Lecture lecture) throws IOException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = Loader.loadPDF(fileBytes)) {
            List<AttachmentUnit> units = new ArrayList<>();

            for (LectureUnitSplitDTO lectureUnit : lectureUnitInformationDTO.units()) {
                // make sure output stream doesn't contain old data
                outputStream.reset();

                AttachmentUnit attachmentUnit = new AttachmentUnit();
                Attachment attachment = new Attachment();
                PDDocumentInformation pdDocumentInformation = new PDDocumentInformation();
                Splitter pdfSplitter = new Splitter();
                pdfSplitter.setStartPage(lectureUnit.startPage());
                pdfSplitter.setEndPage(lectureUnit.endPage());
                // split only based on start and end page
                pdfSplitter.setSplitAtPage(document.getNumberOfPages());

                List<PDDocument> documentUnits = pdfSplitter.split(document);
                pdDocumentInformation.setTitle(lectureUnit.unitName());
                if (!StringUtils.isEmpty(lectureUnitInformationDTO.removeSlidesCommaSeparatedKeyPhrases())) {
                    removeSlidesContainingAnyKeyPhrases(documentUnits.getFirst(), lectureUnitInformationDTO.removeSlidesCommaSeparatedKeyPhrases());
                }
                documentUnits.getFirst().setDocumentInformation(pdDocumentInformation);
                documentUnits.getFirst().save(outputStream);

                // setup attachmentUnit and attachment
                attachmentUnit.setDescription("");
                attachment.setName(lectureUnit.unitName());
                attachment.setAttachmentType(AttachmentType.FILE);
                attachment.setReleaseDate(lectureUnit.releaseDate());
                attachment.setUploadDate(ZonedDateTime.now());

                MultipartFile multipartFile = fileService.convertByteArrayToMultipart(lectureUnit.unitName(), ".pdf", outputStream.toByteArray());
                AttachmentUnit savedAttachmentUnit = attachmentUnitService.createAttachmentUnit(attachmentUnit, attachment, lecture, multipartFile, true);
                slideSplitterService.splitAttachmentUnitIntoSingleSlides(documentUnits.getFirst(), savedAttachmentUnit, multipartFile.getOriginalFilename());
                documentUnits.getFirst().close(); // make sure to close the document
                units.add(savedAttachmentUnit);
            }
            lectureRepository.save(lecture);
            document.close();
            return units;
        }
    }

    /**
     * Gets the slides that should be removed by the given keyphrase
     *
     * @param fileBytes                The byte content of the file (lecture slides) to be split
     * @param commaSeparatedKeyphrases key phrases that identify slides about to be removed
     * @return list of the number of slides that will be removed
     */
    public List<Integer> getSlidesToRemoveByKeyphrase(byte[] fileBytes, String commaSeparatedKeyphrases) {
        List<Integer> slidesToRemove = new ArrayList<>();
        if (commaSeparatedKeyphrases.isEmpty()) {
            return slidesToRemove;
        }
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            List<PDDocument> pages = pdfSinglePageSplitter.split(document);
            List<String> keyphrasesList = getKeyphrasesFromString(commaSeparatedKeyphrases);

            for (int index = 0; index < pages.size(); index++) {
                try (PDDocument currentPage = pages.get(index)) {
                    String slideText = pdfTextStripper.getText(currentPage);

                    if (slideContainsKeyphrase(slideText, keyphrasesList)) {
                        slidesToRemove.add(index);
                    }
                }
            }
        }
        catch (IOException e) {
            log.error("Error while retrieving slides to remove from document", e);
            throw new InternalServerErrorException("Error while retrieving slides to remove from document");
        }
        return slidesToRemove;
    }

    /**
     * Removes the slides containing any of the key phrases from the given document.
     *
     * @param document                 document to remove slides from
     * @param commaSeparatedKeyphrases keyphrases that identify slides about to be removed
     */
    private void removeSlidesContainingAnyKeyPhrases(PDDocument document, String commaSeparatedKeyphrases) {
        try {
            List<PDDocument> pages = pdfSinglePageSplitter.split(document);
            List<String> keyphrasesList = getKeyphrasesFromString(commaSeparatedKeyphrases);

            // Uses a decrementing loop (starting from the last index) to ensure that the
            // index values are adjusted correctly when removing pages.
            for (int index = pages.size() - 1; index >= 0; index--) {
                try (PDDocument currentPage = pages.get(index)) {
                    String slideText = pdfTextStripper.getText(currentPage);

                    if (slideContainsKeyphrase(slideText, keyphrasesList)) {
                        document.removePage(index);
                    }
                }
            }
        }
        catch (IOException e) {
            log.error("Error while removing break slides from document", e);
            throw new InternalServerErrorException("Error while removing break slides from document");
        }
    }

    private boolean slideContainsKeyphrase(String slideText, List<String> keyphrasesList) {
        String lowerCaseSlideText = slideText.toLowerCase();
        return keyphrasesList.stream().anyMatch(keyphrase -> lowerCaseSlideText.contains(keyphrase.strip().toLowerCase()));
    }

    /**
     * Prepare information of split units for client
     *
     * @param fileBytes The byte content of the file (lecture slides) to be split
     * @return The prepared information of split units LectureUnitInformationDTO
     */
    public LectureUnitInformationDTO getSplitUnitData(byte[] fileBytes) {
        try {
            log.debug("Start preparing information of split units.");
            Outline unitsInformation = separateIntoUnits(fileBytes);
            Map<Integer, LectureUnitSplit> unitsDocumentMap = unitsInformation.splits;
            int numberOfPages = unitsInformation.totalPages;

            List<LectureUnitSplitDTO> units = unitsDocumentMap.values().stream()
                    .map(lectureUnitSplit -> new LectureUnitSplitDTO(lectureUnitSplit.unitName, ZonedDateTime.now(), lectureUnitSplit.startPage, lectureUnitSplit.endPage))
                    .toList();
            // return units information, maximum number of pages and by default remove break slides and remove solution slides are false
            return new LectureUnitInformationDTO(units, numberOfPages, null);
        }
        catch (IOException e) {
            log.error("Error while preparing the map with information", e);
            throw new InternalServerErrorException("Could not prepare split information");
        }
    }

    /**
     * Temporarily saves a file that will be processed into lecture units.
     *
     * @param lectureId            the id of the lecture the file belongs to
     * @param file                 the file to be saved
     * @param minutesUntilDeletion duration the file gets saved for
     * @return the last part of the filename. Use {@link LectureUnitProcessingService#getPathForTempFilename(long, String) getPathForTempFilename}
     *         to get the full file path again.
     */
    public String saveTempFileForProcessing(long lectureId, MultipartFile file, int minutesUntilDeletion) throws IOException {
        String prefix = "Temp_" + lectureId + "_";
        String sanitisedFilename = fileService.checkAndSanitizeFilename(file.getOriginalFilename());
        Path filePath = FilePathService.getTempFilePath().resolve(fileService.generateFilename(prefix, sanitisedFilename, false));
        FileUtils.copyInputStreamToFile(file.getInputStream(), filePath.toFile());
        fileService.schedulePathForDeletion(filePath, minutesUntilDeletion);
        return filePath.getFileName().toString().substring(prefix.length());
    }

    /**
     * Gets the path of the temporary file for a give lectureId and filename
     *
     * @param lectureId the id of the lecture the file belongs to
     * @param filename  the last part of the filename (timestamp and extension)
     * @return Path of the file
     */
    public Path getPathForTempFilename(long lectureId, String filename) {
        String fullFilename = "Temp_" + lectureId + "_" + FileService.sanitizeFilename(filename);
        return FilePathService.getTempFilePath().resolve(fullFilename);
    }

    /**
     * This method prepares a map with information on how the slide
     * is going to be split. The map looks like the following:
     * Map<OutlineNumber, (UnitName, StartPage, EndPage)>
     *
     * @param fileBytes The byte content of the file (lecture pdf) to be split
     * @return The prepared map
     */
    private Outline separateIntoUnits(byte[] fileBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            Map<Integer, LectureUnitSplit> outlineMap = new HashMap<>();
            // split the document into single pages
            List<PDDocument> pages = pdfSinglePageSplitter.split(document);
            int numberOfPages = document.getNumberOfPages();
            ListIterator<PDDocument> iterator = pages.listIterator();

            int outlineCount = 0;
            int index = 1;
            while (iterator.hasNext()) {
                PDDocument currentPage = iterator.next();
                String slideText = pdfTextStripper.getText(currentPage);

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

    /**
     * parses a string containing comma-seperated keyphrases into a list of keyphrases.
     */
    private List<String> getKeyphrasesFromString(String commaSeparatedKeyphrases) {
        return Arrays.stream(commaSeparatedKeyphrases.split(",")).filter(s -> !s.isBlank()).toList();
    }
}

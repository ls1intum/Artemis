package de.tum.cit.aet.artemis.web.rest.lecture;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.service.AttachmentUnitService;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.LectureUnitProcessingService;
import de.tum.cit.aet.artemis.service.SlideSplitterService;
import de.tum.cit.aet.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorException;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class AttachmentUnitResource {

    private static final Logger log = LoggerFactory.getLogger(AttachmentUnitResource.class);

    private static final String ENTITY_NAME = "attachmentUnit";

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final GroupNotificationService groupNotificationService;

    private final AttachmentUnitService attachmentUnitService;

    private final LectureUnitProcessingService lectureUnitProcessingService;

    private final CompetencyProgressService competencyProgressService;

    private final SlideSplitterService slideSplitterService;

    private final FileService fileService;

    public AttachmentUnitResource(AttachmentUnitRepository attachmentUnitRepository, LectureRepository lectureRepository, LectureUnitProcessingService lectureUnitProcessingService,
            AuthorizationCheckService authorizationCheckService, GroupNotificationService groupNotificationService, AttachmentUnitService attachmentUnitService,
            CompetencyProgressService competencyProgressService, SlideSplitterService slideSplitterService, FileService fileService) {
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.lectureUnitProcessingService = lectureUnitProcessingService;
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.groupNotificationService = groupNotificationService;
        this.attachmentUnitService = attachmentUnitService;
        this.competencyProgressService = competencyProgressService;
        this.slideSplitterService = slideSplitterService;
        this.fileService = fileService;
    }

    /**
     * GET lectures/:lectureId/attachment-units/:attachmentUnitId : gets the attachment unit with the specified id
     *
     * @param attachmentUnitId the id of the attachmentUnit to retrieve
     * @param lectureId        the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the attachment unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/attachment-units/{attachmentUnitId}")
    @EnforceAtLeastEditor
    public ResponseEntity<AttachmentUnit> getAttachmentUnit(@PathVariable Long attachmentUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get AttachmentUnit : {}", attachmentUnitId);
        AttachmentUnit attachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);
        checkAttachmentUnitCourseAndLecture(attachmentUnit, lectureId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, attachmentUnit.getLecture().getCourse(), null);

        return ResponseEntity.ok().body(attachmentUnit);
    }

    /**
     * PUT lectures/:lectureId/attachment-units/:attachmentUnitId : Updates an existing attachment unit
     *
     * @param lectureId        the id of the lecture to which the attachment unit belongs to update
     * @param attachmentUnitId the id of the attachment unit to update
     * @param attachmentUnit   the attachment unit with updated content
     * @param attachment       the attachment with updated content
     * @param file             the optional file to upload
     * @param keepFilename     specifies if the original filename should be kept or not
     * @param notificationText the text to be used for the notification. No notification will be sent if the parameter is not set
     * @return the ResponseEntity with status 200 (OK) and with body the updated attachmentUnit
     */
    @PutMapping(value = "lectures/{lectureId}/attachment-units/{attachmentUnitId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditor
    public ResponseEntity<AttachmentUnit> updateAttachmentUnit(@PathVariable Long lectureId, @PathVariable Long attachmentUnitId, @RequestPart AttachmentUnit attachmentUnit,
            @RequestPart Attachment attachment, @RequestPart(required = false) MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFilename,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update an attachment unit : {}", attachmentUnit);
        AttachmentUnit existingAttachmentUnit = attachmentUnitRepository.findOneWithSlidesAndCompetencies(attachmentUnitId);
        checkAttachmentUnitCourseAndLecture(existingAttachmentUnit, lectureId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, existingAttachmentUnit.getLecture().getCourse(), null);

        AttachmentUnit savedAttachmentUnit = attachmentUnitService.updateAttachmentUnit(existingAttachmentUnit, attachmentUnit, attachment, file, keepFilename);

        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutAttachmentChange(savedAttachmentUnit.getAttachment(), notificationText);
        }

        return ResponseEntity.ok(savedAttachmentUnit);
    }

    /**
     * POST lectures/:lectureId/attachment-units : creates a new attachment unit.
     *
     * @param lectureId      the id of the lecture to which the attachment unit should be added
     * @param attachmentUnit the attachment unit that should be created
     * @param attachment     the attachment that should be created
     * @param file           the file to upload
     * @param keepFilename   specifies if the original filename should be kept or not
     * @return the ResponseEntity with status 201 (Created) and with body the new attachment unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "lectures/{lectureId}/attachment-units", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditor
    public ResponseEntity<AttachmentUnit> createAttachmentUnit(@PathVariable Long lectureId, @RequestPart AttachmentUnit attachmentUnit, @RequestPart Attachment attachment,
            @RequestPart MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFilename) throws URISyntaxException {
        log.debug("REST request to create AttachmentUnit {} with Attachment {}", attachmentUnit, attachment);
        if (attachmentUnit.getId() != null) {
            throw new BadRequestAlertException("A new attachment unit cannot already have an ID", ENTITY_NAME, "idexists");
        }
        if (attachment.getId() != null) {
            throw new BadRequestAlertException("A new attachment cannot already have an ID", ENTITY_NAME, "idexists");
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        AttachmentUnit savedAttachmentUnit = attachmentUnitService.createAttachmentUnit(attachmentUnit, attachment, lecture, file, keepFilename);
        lectureRepository.save(lecture);
        if (Objects.equals(FilenameUtils.getExtension(file.getOriginalFilename()), "pdf")) {
            slideSplitterService.splitAttachmentUnitIntoSingleSlides(savedAttachmentUnit);
        }
        attachmentUnitService.prepareAttachmentUnitForClient(savedAttachmentUnit);
        competencyProgressService.updateProgressByLearningObjectAsync(savedAttachmentUnit);

        return ResponseEntity.created(new URI("/api/attachment-units/" + savedAttachmentUnit.getId())).body(savedAttachmentUnit);
    }

    /**
     * POST lectures/:lectureId/attachment-units/upload : Temporarily uploads a file which will be processed into lecture units
     *
     * @param file      the file that will be processed
     * @param lectureId the id of the lecture to which the attachment units will be added
     * @return the ResponseEntity with status 200 (ok) and with body filename of the uploaded file
     */
    @PostMapping("lectures/{lectureId}/attachment-units/upload")
    @EnforceAtLeastEditor
    public ResponseEntity<String> uploadSlidesForProcessing(@PathVariable Long lectureId, @RequestPart("file") MultipartFile file) {
        // time until the temporary file gets deleted. Must be greater or equal than MINUTES_UNTIL_DELETION in attachment-units.component.ts
        int minutesUntilDeletion = 30;
        String originalFilename = file.getOriginalFilename();
        log.debug("REST request to upload file: {}", originalFilename);
        checkLecture(lectureId);
        if (!Objects.equals(FilenameUtils.getExtension(originalFilename), "pdf")) {
            throw new BadRequestAlertException("The file must be a pdf", ENTITY_NAME, "wrongFileType");
        }
        try {
            String filename = lectureUnitProcessingService.saveTempFileForProcessing(lectureId, file, minutesUntilDeletion);
            return ResponseEntity.ok().body(new ObjectMapper().writeValueAsString(filename));
        }
        catch (IOException e) {
            log.error("Could not save file {}", originalFilename, e);
            throw new InternalServerErrorException("Could not create file");
        }
    }

    /**
     * POST lectures/:lectureId/attachment-units/split : creates new attachment units from the given file and lecture unit information
     *
     * @param lectureId                 the id of the lecture to which the attachment units will be added
     * @param lectureUnitInformationDTO the units that will be created
     * @param filename                  the name of the lecture file, located in the temp folder
     * @return the ResponseEntity with status 200 (ok) and with body the newly created attachment units
     */
    @PostMapping("lectures/{lectureId}/attachment-units/split/{filename}")
    @EnforceAtLeastEditor
    public ResponseEntity<List<AttachmentUnit>> createAttachmentUnits(@PathVariable Long lectureId, @RequestBody LectureUnitInformationDTO lectureUnitInformationDTO,
            @PathVariable String filename) {
        log.debug("REST request to create AttachmentUnits {} with lectureId {} for file {}", lectureUnitInformationDTO, lectureId, filename);
        checkLecture(lectureId);
        Path filePath = lectureUnitProcessingService.getPathForTempFilename(lectureId, filename);
        checkFile(filePath);

        try {
            byte[] fileBytes = fileService.getFileForPath(filePath);
            List<AttachmentUnit> savedAttachmentUnits = lectureUnitProcessingService.splitAndSaveUnits(lectureUnitInformationDTO, fileBytes,
                    lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId));
            savedAttachmentUnits.forEach(attachmentUnitService::prepareAttachmentUnitForClient);
            savedAttachmentUnits.forEach(competencyProgressService::updateProgressByLearningObjectAsync);
            return ResponseEntity.ok().body(savedAttachmentUnits);
        }
        catch (IOException e) {
            log.error("Could not create attachment units automatically", e);
            throw new InternalServerErrorException("Could not create attachment units automatically");
        }
    }

    /**
     * GET lectures/:lectureId/attachment-units : Calculates lecture units by splitting up the given file
     *
     * @param lectureId the id of the lecture to which the file is going to be split
     * @param filename  the name of the lecture file to be split, located in the temp folder
     * @return the ResponseEntity with status 200 (ok) and with body attachmentUnitsData
     */
    @GetMapping("lectures/{lectureId}/attachment-units/data/{filename}")
    @EnforceAtLeastEditor
    public ResponseEntity<LectureUnitInformationDTO> getAttachmentUnitsData(@PathVariable Long lectureId, @PathVariable String filename) {
        log.debug("REST request to split lecture file : {}", filename);

        checkLecture(lectureId);
        Path filePath = lectureUnitProcessingService.getPathForTempFilename(lectureId, filename);
        checkFile(filePath);

        try {
            byte[] fileBytes = fileService.getFileForPath(filePath);
            LectureUnitInformationDTO attachmentUnitsData = lectureUnitProcessingService.getSplitUnitData(fileBytes);
            return ResponseEntity.ok().body(attachmentUnitsData);
        }
        catch (IOException e) {
            log.error("Could not calculate lecture units automatically", e);
            throw new InternalServerErrorException("Could not calculate lecture units automatically");
        }
    }

    /**
     * GET lectures/:lectureId/attachment-units/slides-to-remove : gets the slides to be removed
     *
     * @param lectureId                the id of the lecture to which the unit belongs
     * @param filename                 the name of the file to be parsed, located in the temp folder
     * @param commaSeparatedKeyPhrases the comma seperated keyphrases to be removed
     * @return the ResponseEntity with status 200 (OK) and with body the list of slides to be removed
     */
    @GetMapping("lectures/{lectureId}/attachment-units/slides-to-remove/{filename}")
    @EnforceAtLeastEditor
    public ResponseEntity<List<Integer>> getSlidesToRemove(@PathVariable Long lectureId, @PathVariable String filename, @RequestParam String commaSeparatedKeyPhrases) {
        log.debug("REST request to get slides to remove for lecture file : {} and keywords : {}", filename, commaSeparatedKeyPhrases);
        checkLecture(lectureId);
        Path filePath = lectureUnitProcessingService.getPathForTempFilename(lectureId, filename);
        checkFile(filePath);

        try {
            byte[] fileBytes = fileService.getFileForPath(filePath);
            List<Integer> slidesToRemove = this.lectureUnitProcessingService.getSlidesToRemoveByKeyphrase(fileBytes, commaSeparatedKeyPhrases);
            return ResponseEntity.ok().body(slidesToRemove);
        }
        catch (IOException e) {
            log.error("Could not calculate slides to remove", e);
            throw new InternalServerErrorException("Could not calculate slides to remove");
        }
    }

    /**
     * Checks that the attachment unit belongs to the specified lecture.
     *
     * @param attachmentUnit The attachment unit to check
     * @param lectureId      The id of the lecture to check against
     */
    private void checkAttachmentUnitCourseAndLecture(AttachmentUnit attachmentUnit, Long lectureId) {
        if (attachmentUnit.getLecture() == null || attachmentUnit.getLecture().getCourse() == null) {
            throw new BadRequestAlertException("Lecture unit must be associated to a lecture of a course", ENTITY_NAME, "lectureOrCourseMissing");
        }
        if (!attachmentUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }
    }

    /**
     * Checks that the lecture exists and is part of a course, and that the user is at least editor in the course
     *
     * @param lectureId The id of the lecture
     */
    private void checkLecture(Long lectureId) {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);
    }

    /**
     * Checks the file exists on the server and is a .pdf
     *
     * @param filePath the path of the file
     */
    private void checkFile(Path filePath) {
        if (!Files.exists(filePath)) {
            throw new EntityNotFoundException(ENTITY_NAME, filePath.toString());
        }
        if (!filePath.toString().endsWith(".pdf")) {
            throw new BadRequestAlertException("The file must be a pdf", ENTITY_NAME, "wrongFileType");
        }
    }
}

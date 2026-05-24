package de.tum.cit.aet.artemis.exam.service;

import java.awt.Rectangle;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.function.TriConsumer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.ImageDTO;
import de.tum.cit.aet.artemis.core.dto.UserForRegistrationDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.ExamStudentDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamStudentSearchDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUsersNotFoundDTO;
import de.tum.cit.aet.artemis.exam.dto.ExportExamUserDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamSeatDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;

/**
 * Service Implementation for managing Exam Users.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamUserService {

    private static final Logger log = LoggerFactory.getLogger(ExamUserService.class);

    private static final String ENTITY_NAME = "examUserService";

    private final ExamUserRepository examUserRepository;

    private final UserRepository userRepository;

    private final FileService fileService;

    private final ExamRoomRepository examRoomRepository;

    private final ExamRoomService examRoomService;

    private final StudentExamRepository studentExamRepository;

    public ExamUserService(FileService fileService, UserRepository userRepository, ExamUserRepository examUserRepository, ExamRoomRepository examRoomRepository,
            ExamRoomService examRoomService, StudentExamRepository studentExamRepository) {
        this.examUserRepository = examUserRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.examRoomRepository = examRoomRepository;
        this.examRoomService = examRoomService;
        this.studentExamRepository = studentExamRepository;
    }

    /**
     * Extracts all images from PDF file and map each image with student registration number.
     *
     * @param file PDF file to be parsed
     * @return list of ExamUserWithImageDTO
     */
    public List<ExamUserWithImageDTO> parsePDF(MultipartFile file) {

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            ImageExtractor imageExtractor = new ImageExtractor(document);
            imageExtractor.process();
            List<ImageDTO> images = imageExtractor.getImages();
            List<ExamUserWithImageDTO> studentWithImages = new ArrayList<>();

            for (ImageDTO image : images) {
                final var stripper = getPdfTextStripperByArea(image);
                stripper.extractRegions(document.getPage(image.page() - 1));
                String string = stripper.getTextForRegion("image:" + (image.page() - 1));
                String[] studentInformation = string.split("\\s");

                if (StringUtils.hasText(string) && studentInformation.length > 1 && studentInformation[1].matches("^[0-9]{8}$")) {
                    // if the string is only numbers and has 8 digits, then it is the registration number
                    // and it should be the second element in the array of the string
                    studentWithImages.add(new ExamUserWithImageDTO(studentInformation[1], image));
                }
                else {
                    studentWithImages.add(new ExamUserWithImageDTO("", image));
                }
            }
            document.close();
            return studentWithImages;
        }
        catch (IOException e) {
            log.error("Error while parsing PDF file", e);
            throw new InternalServerErrorException("Error while parsing PDF file");
        }
    }

    private static PDFTextStripperByArea getPdfTextStripperByArea(ImageDTO image) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);

        // A4 page, where the Y coordinate of the bottom is 0 and the Y coordinate of the top is 842.
        // If you have Y coordinates such as y1 = 806 and y2 = 36, then you can do this: y = 842 - y;
        // and to get left upper corner of the image you subtract the height of the image from the y coordinate
        Rectangle rect = new Rectangle(Math.round(image.xPosition()), 842 - (Math.round(image.yPosition())) - image.renderedHeight(), 4 * image.renderedWidth(),
                image.renderedHeight());
        stripper.addRegion("image:" + (image.page() - 1), rect);
        return stripper;
    }

    /**
     * Saves all images from PDF file
     *
     * @param examId the examId to which the images and exam users will belong
     * @param file   PDF file to be parsed
     * @return list of ExamUserWithImageDTO
     */
    public ExamUsersNotFoundDTO saveImages(long examId, MultipartFile file) {
        log.debug("Save images for file: {}", file);
        List<String> notFoundExamUsersRegistrationNumbers = new ArrayList<>();
        List<ExamUserWithImageDTO> examUserWithImageDTOs = parsePDF(file);

        for (var examUserWithImageDTO : examUserWithImageDTOs) {
            Optional<User> user = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(examUserWithImageDTO.studentRegistrationNumber());
            if (user.isEmpty()) {
                notFoundExamUsersRegistrationNumbers.add(examUserWithImageDTO.studentRegistrationNumber());
                continue;
            }
            Optional<ExamUser> examUserOptional = examUserRepository.findByExamIdAndUserId(examId, user.get().getId());
            if (examUserOptional.isEmpty()) {
                notFoundExamUsersRegistrationNumbers.add(examUserWithImageDTO.studentRegistrationNumber());
                continue;
            }

            ExamUser examUser = examUserOptional.get();
            String oldPathString = examUser.getStudentImagePath();
            MultipartFile studentImageFile = FileUtil.convertByteArrayToMultipart("student_image", ".png", examUserWithImageDTO.image().imageInBytes());
            Path basePath = FilePathConverter.getStudentImageFilePath().resolve(examUser.getId().toString());
            Path savedPath = FileUtil.saveFile(studentImageFile, basePath, FilePathType.EXAM_USER_IMAGE, true);

            examUser.setStudentImagePath(FilePathConverter.externalUriForFileSystemPath(savedPath, FilePathType.EXAM_USER_IMAGE, examUser.getId()).toString());
            examUserRepository.save(examUser);

            if (oldPathString != null) {
                Path oldPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(oldPathString), FilePathType.EXAM_USER_IMAGE);
                fileService.schedulePathForDeletion(oldPath, 0);
            }
        }

        return new ExamUsersNotFoundDTO(notFoundExamUsersRegistrationNumbers.size(), examUserWithImageDTOs.size() - notFoundExamUsersRegistrationNumbers.size(),
                notFoundExamUsersRegistrationNumbers);
    }

    /**
     * Deletes signature and student image of an exam user if they exist
     *
     * @param user the exam user whose images should be deleted
     */
    public void deleteAvailableExamUserImages(ExamUser user) {
        Optional.ofNullable(user.getSigningImagePath()).map(URI::create).map(uri -> FilePathConverter.fileSystemPathForExternalUri(uri, FilePathType.EXAM_USER_SIGNATURE))
                .ifPresent(path -> fileService.schedulePathForDeletion(path, 0));

        Optional.ofNullable(user.getStudentImagePath()).map(URI::create).map(uri -> FilePathConverter.fileSystemPathForExternalUri(uri, FilePathType.EXAM_USER_IMAGE))
                .ifPresent(path -> fileService.schedulePathForDeletion(path, 0));
    }

    /**
     * Contains the information about an exam user with image
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamUserWithImageDTO(String studentRegistrationNumber, ImageDTO image) {
    }

    /**
     * Sets the transient room and seat fields for all {@link ExamUser}s.
     * The exam users must all belong to the same exam.
     *
     * @param examUsers                  All exam users for which the transient fields should be set.
     * @param roomGetter                 The getter function to get the room string from the exam user.
     * @param seatGetter                 The getter function to get the seat string from the exam user.
     * @param transientRoomAndSeatSetter The setter function to set the transient room and seat fields of the exam user.
     */
    private void setRoomAndSeatTransientForExamUsers(Set<ExamUser> examUsers, Function<ExamUser, String> roomGetter, Function<ExamUser, String> seatGetter,
            TriConsumer<ExamUser, ExamRoom, ExamSeatDTO> transientRoomAndSeatSetter) {
        List<Exam> usedExams = examUsers.stream().map(ExamUser::getExam).distinct().toList();
        if (usedExams.size() != 1) {
            throw new BadRequestAlertException("All exam users must belong to the same exam", ENTITY_NAME, "examUserService.multipleExams", Map.of("foundExams", usedExams.size()));
        }
        long examId = usedExams.getFirst().getId();
        Set<ExamRoom> examRoomsUsedInExam = examRoomRepository.findAllByExamId(examId);

        if (examRoomsUsedInExam.isEmpty()) {
            // pure legacy distribution
            return;
        }

        for (ExamUser examUser : examUsers) {
            final String roomNumber = roomGetter.apply(examUser);
            final String seatName = seatGetter.apply(examUser);

            if (!StringUtils.hasText(roomNumber) || !StringUtils.hasText(seatName)) {
                // examUser does not have this location data
                continue;
            }

            Optional<ExamRoom> matchingRoomEntity = examRoomsUsedInExam.stream().filter(room -> room.getRoomNumber().equalsIgnoreCase(roomNumber)).findFirst();
            if (matchingRoomEntity.isEmpty()) {
                // we can't just return here in order to not break exams where a mix of the modern and legacy system was used.
                // while this is discouraged, it would be naive to not account for that possibility.
                // if we were to return here, a single legacy-inserted student could steal the modern mode from modern-inserted ones.
                continue;
            }

            Optional<ExamSeatDTO> matchingSeatEntity = matchingRoomEntity.get().getSeats().stream().filter(seat -> seat.name().equalsIgnoreCase(seatName)).findFirst();
            if (matchingSeatEntity.isEmpty()) {
                // reasoning analogue to the empty matching room case
                continue;
            }

            transientRoomAndSeatSetter.accept(examUser, matchingRoomEntity.get(), matchingSeatEntity.get());
        }
    }

    /**
     * @param examUsers All exam users for which the transient fields should be set.
     * @see #setRoomAndSeatTransientForExamUsers
     */
    public void setPlannedRoomAndSeatTransientForExamUsers(Set<ExamUser> examUsers) {
        setRoomAndSeatTransientForExamUsers(examUsers, ExamUser::getPlannedRoom, ExamUser::getPlannedSeat, ExamUser::setTransientPlannedRoomAndSeat);
    }

    /**
     * @param examUsers All exam users for which the transient fields should be set.
     * @see #setRoomAndSeatTransientForExamUsers
     */
    public void setActualRoomAndSeatTransientForExamUsers(Set<ExamUser> examUsers) {
        setRoomAndSeatTransientForExamUsers(examUsers, ExamUser::getActualRoom, ExamUser::getActualSeat, ExamUser::setTransientActualRoomAndSeat);
    }

    public void checkExamUserExistsAndBelongsToExamElseThrow(long examUserId, long examId) {
        examUserRepository.findWithExamById(examUserId).filter(examUser -> examUser.getExam().getId() == examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam user with id: " + examUserId + " does not exist in exam with id: " + examId));
    }

    /**
     * Gathers information about students for exporting
     *
     * @param examId the exam id
     * @return all exam users with their most important data for exporting
     */
    public List<ExportExamUserDTO> exportStudents(long examId) {
        List<ExamUser> studentsInExam = examUserRepository.findAllByExamId(examId);
        Set<ExamRoom> roomsUsedInExam = examRoomRepository.findAllByExamId(examId);
        Map<String, String> roomNumbersToHumanReadable = roomsUsedInExam.stream().collect(Collectors.toMap(ExamRoom::getRoomNumber, examRoomService::humanReadableFormat));

        return studentsInExam.stream()
                .map(examUser -> new ExportExamUserDTO(examUser, roomNumbersToHumanReadable.getOrDefault(examUser.getPlannedRoom(), examUser.getPlannedRoom()))).toList();
    }

    /**
     * Returns a page of {@link ExamStudentDTO} for the given exam.
     * Pagination and sorting are applied on a lightweight ID query first, then the current page's
     * {@link ExamUser} entities and their {@link ExamStudentDTO.StudentExamSummary} data are fetched in two
     * targeted queries — one per entity type, both scoped to the current page only.
     *
     * @param examId the exam to query
     * @param search search term, page index, page size, sort column, sort direction, and an optional filter value (e.g. {@code "Submitted"}, {@code "AttendanceNotChecked"})
     * @return a page of {@link ExamStudentDTO} in the requested order
     */
    public Page<ExamStudentDTO> findExamStudentsForExamPaged(long examId, ExamStudentSearchDTO search) {
        Page<Long> idPage = examUserRepository.findExamUserIdsForExam(examId, search);
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), idPage.getPageable(), idPage.getTotalElements());
        }

        List<ExamUser> examUsers = examUserRepository.findByIdsWithUser(ids);
        Map<Long, ExamUser> examUserById = examUsers.stream().collect(Collectors.toMap(ExamUser::getId, Function.identity()));

        List<Long> userIds = examUsers.stream().map(eu -> eu.getUser() != null ? eu.getUser().getId() : null).filter(Objects::nonNull).toList();
        Map<Long, ExamStudentDTO.StudentExamSummary> summaryByUserId = studentExamRepository.findSummaryByExamIdAndUserIds(examId, userIds).stream()
                .collect(Collectors.toMap(ExamStudentDTO.StudentExamSummary::userId, Function.identity(), (a, b) -> a));

        List<ExamStudentDTO> dtos = ids.stream().map(examUserById::get).filter(Objects::nonNull).map(eu -> mapToExamStudentDTO(eu, summaryByUserId)).toList();

        return new PageImpl<>(dtos, idPage.getPageable(), idPage.getTotalElements());
    }

    /**
     * Searches Artemis users by login prefix, full-name substring, email substring, or registration-number substring,
     * and marks each result as already registered for the given exam.
     *
     * @param examId     the exam to check existing registrations against
     * @param searchTerm the text entered by the instructor
     * @param page       zero-based page index
     * @param size       number of results per page
     * @return a page of {@link UserForRegistrationDTO} with {@code isRegistered} set appropriately
     */
    public Page<UserForRegistrationDTO> searchUsersForExamRegistration(long examId, String searchTerm, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<User> users = userRepository.searchAllByLoginOrNameOrEmailOrRegistrationNumber(pageable, searchTerm);

        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Set<Long> registeredIds = userIds.isEmpty() ? Set.of() : examUserRepository.findRegisteredUserIdsByExamIdAndUserIds(examId, userIds);

        List<UserForRegistrationDTO> dtos = users.getContent().stream().map(user -> new UserForRegistrationDTO(user.getId(), user.getLogin(), user.getName(), user.getEmail(),
                user.getRegistrationNumber(), user.getImageUrl(), registeredIds.contains(user.getId()))).toList();

        return new PageImpl<>(dtos, pageable, users.getTotalElements());
    }

    private ExamStudentDTO mapToExamStudentDTO(ExamUser eu, Map<Long, ExamStudentDTO.StudentExamSummary> summaryByUserId) {
        User user = eu.getUser();
        Long userId = user != null ? user.getId() : null;
        String login = user != null ? user.getLogin() : null;
        String email = user != null ? user.getEmail() : null;
        String registrationNumber = user != null ? user.getRegistrationNumber() : null;
        String firstName = user != null && user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user != null && user.getLastName() != null ? user.getLastName() : "";
        String name = (firstName + " " + lastName).trim();

        ExamStudentDTO.StudentExamSummary se = userId != null ? summaryByUserId.get(userId) : null;

        Long studentExamId = se != null ? se.studentExamId() : null;
        Integer workingTime = se != null ? se.workingTime() : null;
        Boolean started = se != null ? se.started() : null;
        Boolean submitted = se != null ? se.submitted() : null;
        ZonedDateTime startedDate = se != null ? se.startedDate() : null;
        ZonedDateTime submissionDate = se != null ? se.submissionDate() : null;
        long examSessionCount = se != null ? se.examSessionCount() : 0L;
        boolean didAttend = Boolean.TRUE.equals(started);

        String progress;
        if (se == null) {
            progress = ExamStudentDTO.PROGRESS_EXAM_MISSING;
        }
        else if (Boolean.TRUE.equals(submitted)) {
            progress = ExamStudentDTO.PROGRESS_SUBMITTED;
        }
        else if (Boolean.TRUE.equals(started)) {
            progress = ExamStudentDTO.PROGRESS_STARTED;
        }
        else {
            progress = ExamStudentDTO.PROGRESS_NOT_STARTED;
        }

        return new ExamStudentDTO(eu.getId(), userId, login, name, email, registrationNumber, eu.getStudentImagePath(), eu.getPlannedRoom(), eu.getActualRoom(),
                eu.getPlannedSeat(), eu.getActualSeat(), eu.getDidCheckImage(), eu.getDidCheckName(), eu.getDidCheckLogin(), eu.getDidCheckRegistrationNumber(),
                eu.getSigningImagePath(), didAttend, studentExamId, workingTime, started, submitted, startedDate, submissionDate, examSessionCount, progress);
    }
}

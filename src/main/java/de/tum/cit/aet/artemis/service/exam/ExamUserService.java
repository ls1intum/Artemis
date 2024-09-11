package de.tum.cit.aet.artemis.service.exam;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.awt.Rectangle;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.exam.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.web.rest.dto.ExamUsersNotFoundDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ImageDTO;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service Implementation for managing Exam Users.
 */
@Profile(PROFILE_CORE)
@Service
public class ExamUserService {

    private static final Logger log = LoggerFactory.getLogger(ExamUserService.class);

    private final ExamUserRepository examUserRepository;

    private final UserRepository userRepository;

    private final FileService fileService;

    public ExamUserService(FileService fileService, UserRepository userRepository, ExamUserRepository examUserRepository) {
        this.examUserRepository = examUserRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
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
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                stripper.setSortByPosition(true);

                // A4 page, where the Y coordinate of the bottom is 0 and the Y coordinate of the top is 842.
                // If you have Y coordinates such as y1 = 806 and y2 = 36, then you can do this: y = 842 - y;
                // and to get left upper corner of the image you subtract the height of the image from the y coordinate
                Rectangle rect = new Rectangle(Math.round(image.xPosition()), 842 - (Math.round(image.yPosition())) - image.renderedHeight(), 4 * image.renderedWidth(),
                        image.renderedHeight());
                stripper.addRegion("image:" + (image.page() - 1), rect);
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
            MultipartFile studentImageFile = fileService.convertByteArrayToMultipart("student_image", ".png", examUserWithImageDTO.image().imageInBytes());
            Path savedPath = fileService.saveFile(studentImageFile, FilePathService.getStudentImageFilePath(), false);

            examUser.setStudentImagePath(FilePathService.publicPathForActualPathOrThrow(savedPath, examUser.getId()).toString());
            examUserRepository.save(examUser);

            if (oldPathString != null) {
                Path oldPath = FilePathService.actualPathForPublicPath(URI.create(oldPathString));
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
        Stream.of(user.getSigningImagePath(), user.getStudentImagePath()).filter(Objects::nonNull).map(URI::create).map(FilePathService::actualPathForPublicPath)
                .forEach(path -> fileService.schedulePathForDeletion(path, 0));
    }

    /**
     * Contains the information about an exam user with image
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamUserWithImageDTO(String studentRegistrationNumber, ImageDTO image) {
    }
}

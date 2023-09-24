package de.tum.in.www1.artemis.service.exam;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.web.rest.dto.ExamUsersNotFoundDTO;
import de.tum.in.www1.artemis.web.rest.dto.ImageDTO;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service Implementation for managing Exam Users.
 */
@Service
public class ExamUserService {

    private final Logger log = LoggerFactory.getLogger(ExamUserService.class);

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

        examUserWithImageDTOs.forEach(examUserWithImageDTO -> {
            Optional<User> user = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(examUserWithImageDTO.studentRegistrationNumber());
            if (user.isPresent()) {
                Optional<ExamUser> examUserOptional = examUserRepository.findByExamIdAndUserId(examId, user.get().getId());
                if (examUserOptional.isPresent()) {
                    ExamUser examUser = examUserOptional.get();
                    MultipartFile studentImageFile = fileService.convertByteArrayToMultipart(examUserWithImageDTO.studentRegistrationNumber() + "_student_image", ".png",
                            examUserWithImageDTO.image().imageInBytes());
                    String responsePath = fileService.handleSaveFile(studentImageFile, false, false).toString();
                    examUser.setStudentImagePath(responsePath);
                    examUserRepository.save(examUser);
                }
                else {
                    notFoundExamUsersRegistrationNumbers.add(examUserWithImageDTO.studentRegistrationNumber());
                }
            }
            else {
                notFoundExamUsersRegistrationNumbers.add(examUserWithImageDTO.studentRegistrationNumber());
            }
        });

        return new ExamUsersNotFoundDTO(notFoundExamUsersRegistrationNumbers.size(), examUserWithImageDTOs.size() - notFoundExamUsersRegistrationNumbers.size(),
                notFoundExamUsersRegistrationNumbers);
    }

    /**
     * Contains the information about an exam user with image
     */
    record ExamUserWithImageDTO(String studentRegistrationNumber, ImageDTO image) {
    }
}

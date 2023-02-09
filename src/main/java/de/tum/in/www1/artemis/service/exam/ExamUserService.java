package de.tum.in.www1.artemis.service.exam;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.web.rest.dto.ImageDTO;

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

        try (PDDocument document = PDDocument.load(file.getBytes())) {
            ImageExtractor imageExtractor = new ImageExtractor(document);
            imageExtractor.process();
            List<ImageDTO> images = imageExtractor.getImages();
            List<ExamUserWithImageDTO> studentWithImages = new ArrayList<>();

            System.out.println(images.size());
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

                studentWithImages.add(new ExamUserWithImageDTO(string != null ? string.split("\\s").length > 0 ? string.split("\\s")[1] : "no registration number" : "", image));
            }
            document.close();
            return studentWithImages;
        }
        catch (IOException e) {
            log.error("Error while parsing PDF file", e);
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Saves all images from PDF file
     *
     * @param file PDF file to be parsed
     * @return list of ExamUserWithImageDTO
     */
    public Set<String> saveImages(long examId, MultipartFile file) {
        log.debug("Save images for file: {}", file);
        Set<String> notFoundExamUsersRegistrationNumbers = new HashSet<>();
        List<ExamUserWithImageDTO> examUserWithImageDTOs = parsePDF(file);

        examUserWithImageDTOs.forEach(examUserWithImageDTO -> {
            Optional<User> user = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(examUserWithImageDTO.studentRegistrationNumber());
            if (user.isPresent()) {
                ExamUser examUser = examUserRepository.findByExamIdAndUserId(examId, user.get().getId());
                if (examUser == null) {
                    notFoundExamUsersRegistrationNumbers.add(examUserWithImageDTO.studentRegistrationNumber());
                }
                else {
                    MultipartFile studentImage = fileService.convertByteArrayToMultipart(examUserWithImageDTO.studentRegistrationNumber() + "_student_image", ".png",
                            examUserWithImageDTO.image().imageInBytes());
                    String responsePath = fileService.handleSaveFile(studentImage, false, false);
                    examUser.setStudentImagePath(responsePath);
                    examUserRepository.save(examUser);
                }
            }
            else {
                notFoundExamUsersRegistrationNumbers.add(examUserWithImageDTO.studentRegistrationNumber());
            }
        });
        return notFoundExamUsersRegistrationNumbers;
    }

    /**
     * Contains the information about an exam user with image
     */
    private record ExamUserWithImageDTO(String studentRegistrationNumber, ImageDTO image) {
    }
}

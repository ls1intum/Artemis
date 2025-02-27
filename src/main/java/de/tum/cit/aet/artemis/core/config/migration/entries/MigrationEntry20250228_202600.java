package de.tum.cit.aet.artemis.core.config.migration.entries;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.config.migration.MigrationEntry;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadSubmissionRepository;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.repository.DragItemRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;

/**
 * This migration entry updates all file paths in the database that point to files in the file system. The file paths are updated to the new file path prefix.
 * The new file path prefix is /api/core/files instead of /api/files.
 * See {@link de.tum.cit.aet.artemis.core.service.FilePathService} for more information on the resources using these file paths.
 */
public class MigrationEntry20250228_202600 extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20250228_202600.class);

    private final AttachmentRepository attachmentRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final DragItemRepository dragItemRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    public MigrationEntry20250228_202600(AttachmentRepository attachmentRepository, QuizQuestionRepository quizQuestionRepository, DragItemRepository dragItemRepository,
            CourseRepository courseRepository, UserRepository userRepository, ExamUserRepository examUserRepository, FileUploadExerciseRepository fileUploadExerciseRepository,
            FileUploadSubmission fileUploadSubmission, FileUploadSubmissionRepository fileUploadSubmissionRepository) {
        this.attachmentRepository = attachmentRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.dragItemRepository = dragItemRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.examUserRepository = examUserRepository;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
    }

    @Override
    public void execute() {
        log.info("Starting migration entry 20250228_202600");

        updateAttachmentLinks();
        updateQuizQuestionBackgroundImagePaths();
        updateDragItemFilePaths();
        updateCourseIconPaths();
        updateUserImageUrls();
        updateStudentExamAssetPaths();
        updateFileUploadSubmissionPaths();
    }

    private String updatePrefix(String link) {
        return link.replace("/api/files", "/api/core/files");
    }

    private void updateAttachmentLinks() {
        log.info("Updating attachment links");
        List<Attachment> attachments = attachmentRepository.findAll();
        attachments.forEach(attachment -> {
            String link = attachment.getLink();
            if (link == null) {
                log.info("Attachment {} has no link. Skipping...", attachment.getId());
                return;
            }

            if (!link.startsWith("/api/files")) {
                log.info("Attachment {} has a link that does not start with /api/files: {}. Skipping...", attachment.getId(), link);
                return;
            }

            link = updatePrefix(link);
            attachment.setLink(link);
        });
        attachmentRepository.saveAll(attachments);
        log.info("Updated {} attachments", attachments.size());
    }

    private void updateQuizQuestionBackgroundImagePaths() {
        log.info("Updating quiz question background file paths");
        List<QuizQuestion> quizQuestions = quizQuestionRepository.findAll();
        quizQuestions.stream().filter(quizQuestion -> quizQuestion instanceof DragAndDropQuestion).forEach(quizQuestion -> {
            String link = ((DragAndDropQuestion) quizQuestion).getBackgroundFilePath();
            if (link == null) {
                log.info("Quiz question {} has no background file paths. Skipping...", quizQuestion.getId());
                return;
            }

            if (!link.startsWith("/api/files")) {
                log.info("Quiz question {} has a background file paths that does not start with /api/files: {}. Skipping...", quizQuestion.getId(), link);
                return;
            }

            link = updatePrefix(link);
            ((DragAndDropQuestion) quizQuestion).setBackgroundFilePath(link);
        });
        quizQuestionRepository.saveAll(quizQuestions);
        log.info("Updated {} quiz questions", quizQuestions.size());
    }

    private void updateDragItemFilePaths() {
        log.info("Updating drag item picture file path");
        List<DragItem> dragItems = dragItemRepository.findAll();
        dragItems.forEach(dragItem -> {
            String link = dragItem.getPictureFilePath();
            if (link == null) {
                log.info("Drag item {} has no picture file path. Skipping...", dragItem.getId());
                return;
            }

            if (!link.startsWith("/api/files")) {
                log.info("Drag item {} has a picture file path that does not start with /api/files: {}. Skipping...", dragItem.getId(), link);
                return;
            }

            link = updatePrefix(link);
            dragItem.setPictureFilePath(link);
        });
        dragItemRepository.saveAll(dragItems);
        log.info("Updated {} drag items", dragItems.size());
    }

    private void updateCourseIconPaths() {
        log.info("Updating course icons");
        List<Course> courses = courseRepository.findAll();
        courses.forEach(course -> {
            String link = course.getCourseIcon();
            if (link == null) {
                log.info("Course {} has no icon. Skipping...", course.getId());
                return;
            }

            if (!link.startsWith("/api/files")) {
                log.info("Course {} has an icon that does not start with /api/files: {}. Skipping...", course.getId(), link);
                return;
            }

            link = updatePrefix(link);
            course.setCourseIcon(link);
        });
        courseRepository.saveAll(courses);
        log.info("Updated {} courses", courses.size());
    }

    private void updateUserImageUrls() {
        log.info("Updating user image urls");
        List<User> users = userRepository.findAll();
        users.forEach(user -> {
            String link = user.getImageUrl();
            if (link == null) {
                log.info("User {} has no image url. Skipping...", user.getId());
                return;
            }

            if (!link.startsWith("/api/files")) {
                log.info("User {} has an image url that does not start with /api/files: {}. Skipping...", user.getId(), link);
                return;
            }

            link = updatePrefix(link);
            user.setImageUrl(link);
        });
        userRepository.saveAll(users);
        log.info("Updated {} users", users.size());
    }

    private void updateStudentExamAssetPaths() {
        log.info("Updating exam user asset paths");
        List<ExamUser> examUsers = examUserRepository.findAll();
        examUsers.forEach(examUser -> {
            String signingImagePath = examUser.getSigningImagePath();
            if (signingImagePath == null) {
                log.info("Exam user {} has no signature image path. Skipping...", examUser.getId());
                return;
            }

            if (!signingImagePath.startsWith("/api/files")) {
                log.info("Exam user {} has a signature image path that does not start with /api/files: {}. Skipping...", examUser.getId(), signingImagePath);
                return;
            }

            signingImagePath = updatePrefix(signingImagePath);
            examUser.setSigningImagePath(signingImagePath);

            String studentImagePath = examUser.getStudentImagePath();
            if (studentImagePath == null) {
                log.info("Exam user {} has no student image. Skipping...", examUser.getId());
                return;
            }

            if (!studentImagePath.startsWith("/api/files")) {
                log.info("Exam user {} has a student image that does not start with /api/files: {}. Skipping...", examUser.getId(), studentImagePath);
                return;
            }

            studentImagePath = updatePrefix(studentImagePath);
            examUser.setStudentImagePath(studentImagePath);
        });
        examUserRepository.saveAll(examUsers);
        log.info("Updated {} exam users", examUsers.size());
    }

    private void updateFileUploadSubmissionPaths() {
        log.info("Updating file upload submissions");
        List<FileUploadSubmission> fileUploadSubmissions = fileUploadSubmissionRepository.findAll();
        fileUploadSubmissions.forEach(fileUploadSubmission -> {
            String link = fileUploadSubmission.getFilePath();
            if (link == null) {
                log.info("File upload submission {} has no file path. Skipping...", fileUploadSubmission.getId());
                return;
            }

            if (!link.startsWith("/api/files")) {
                log.info("File upload submission {} has a file path that does not start with /api/files: {}. Skipping...", fileUploadSubmission.getId(), link);
                return;
            }

            link = updatePrefix(link);
            fileUploadSubmission.setFilePath(link);
        });
        fileUploadSubmissionRepository.saveAll(fileUploadSubmissions);
        log.info("Updated {} file upload submissions", fileUploadSubmissions.size());
    }

    @Override
    public String author() {
        return "ole-ve";
    }

    @Override
    public String date() {
        return "20250228_202600";
    }
}

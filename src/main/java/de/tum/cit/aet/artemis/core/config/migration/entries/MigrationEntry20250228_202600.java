package de.tum.cit.aet.artemis.core.config.migration.entries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.config.migration.MigrationEntry;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
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

    private static final int ATTACHMENTS_BATCH_SIZE = 300;

    private static final int QUIZ_QUESTIONS_BATCH_SIZE = 300;

    private static final int DRAG_ITEMS_BATCH_SIZE = 300;

    private static final int COURSES_BATCH_SIZE = 100;

    private static final int USERS_BATCH_SIZE = 300;

    private static final int EXAM_USERS_BATCH_SIZE = 100;

    private static final int FILE_UPLOAD_SUBMISSIONS_BATCH_SIZE = 300;

    private final AttachmentRepository attachmentRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final DragItemRepository dragItemRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    public MigrationEntry20250228_202600(AttachmentRepository attachmentRepository, QuizQuestionRepository quizQuestionRepository, DragItemRepository dragItemRepository,
            CourseRepository courseRepository, UserRepository userRepository, ExamUserRepository examUserRepository,
            FileUploadSubmissionRepository fileUploadSubmissionRepository) {
        this.attachmentRepository = attachmentRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.dragItemRepository = dragItemRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.examUserRepository = examUserRepository;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
    }

    @Override
    @Transactional
    public void execute() {
        updateAttachmentLinks(ATTACHMENTS_BATCH_SIZE);
        updateQuizQuestionBackgroundImagePaths(QUIZ_QUESTIONS_BATCH_SIZE);
        updateDragItemFilePaths(DRAG_ITEMS_BATCH_SIZE);
        updateCourseIconPaths(COURSES_BATCH_SIZE);
        updateUserImageUrls(USERS_BATCH_SIZE);
        updateStudentExamAssetPaths(EXAM_USERS_BATCH_SIZE);
        updateFileUploadSubmissionPaths(FILE_UPLOAD_SUBMISSIONS_BATCH_SIZE);
    }

    private String updatePrefix(String link) {
        return link.replace("/api/files", "/api/core/files");
    }

    @Transactional
    protected void updateAttachmentLinks(int batchSize) {
        log.info("Updating attachment links with batch size: {}", batchSize);
        int page = 0;
        long totalUpdated = 0;
        Page<Attachment> attachmentsPage;

        do {
            attachmentsPage = attachmentRepository.findAll(PageRequest.of(page, batchSize));

            for (Attachment attachment : attachmentsPage.getContent()) {
                String link = attachment.getLink();
                if (link == null) {
                    log.info("Attachment {} has no link. Skipping...", attachment.getId());
                    continue;
                }

                if (!link.startsWith("/api/files")) {
                    log.info("Attachment {} has a link that does not start with /api/files: {}. Skipping...", attachment.getId(), link);
                    continue;
                }

                link = updatePrefix(link);
                attachment.setLink(link);
                totalUpdated++;
            }

            attachmentRepository.saveAll(attachmentsPage.getContent());
            page++;
        }
        while (attachmentsPage.hasNext());

        log.info("Updated {} attachments", totalUpdated);
    }

    @Transactional
    protected void updateQuizQuestionBackgroundImagePaths(int batchSize) {
        log.info("Updating quiz question background file paths with batch size: {}", batchSize);
        int page = 0;
        long totalUpdated = 0;
        Page<QuizQuestion> quizQuestionsPage;

        do {
            quizQuestionsPage = quizQuestionRepository.findAll(PageRequest.of(page, batchSize));

            for (QuizQuestion quizQuestion : quizQuestionsPage.getContent()) {
                if (!(quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion)) {
                    continue;
                }

                String link = dragAndDropQuestion.getBackgroundFilePath();

                if (link == null) {
                    log.info("Quiz question {} has no background file paths. Skipping...", quizQuestion.getId());
                    continue;
                }

                if (!link.startsWith("/api/files")) {
                    log.info("Quiz question {} has a background file paths that does not start with /api/files: {}. Skipping...", quizQuestion.getId(), link);
                    continue;
                }

                link = updatePrefix(link);
                dragAndDropQuestion.setBackgroundFilePath(link);
                totalUpdated++;
            }

            quizQuestionRepository.saveAll(quizQuestionsPage.getContent());
            page++;
        }
        while (quizQuestionsPage.hasNext());

        log.info("Updated {} quiz questions", totalUpdated);
    }

    @Transactional
    protected void updateDragItemFilePaths(int batchSize) {
        log.info("Updating drag item picture file path with batch size: {}", batchSize);
        int page = 0;
        long totalUpdated = 0;
        Page<DragItem> dragItemsPage;

        do {
            dragItemsPage = dragItemRepository.findAll(PageRequest.of(page, batchSize));

            for (DragItem dragItem : dragItemsPage.getContent()) {
                String link = dragItem.getPictureFilePath();
                if (link == null) {
                    log.info("Drag item {} has no picture file path. Skipping...", dragItem.getId());
                    continue;
                }

                if (!link.startsWith("/api/files")) {
                    log.info("Drag item {} has a picture file path that does not start with /api/files: {}. Skipping...", dragItem.getId(), link);
                    continue;
                }

                link = updatePrefix(link);
                dragItem.setPictureFilePath(link);
                totalUpdated++;
            }

            dragItemRepository.saveAll(dragItemsPage.getContent());
            page++;
        }
        while (dragItemsPage.hasNext());

        log.info("Updated {} drag items", totalUpdated);
    }

    @Transactional
    protected void updateCourseIconPaths(int batchSize) {
        log.info("Updating course icons with batch size: {}", batchSize);
        int page = 0;
        long totalUpdated = 0;
        Page<Course> coursesPage;

        do {
            coursesPage = courseRepository.findAll(PageRequest.of(page, batchSize));

            for (Course course : coursesPage.getContent()) {
                String link = course.getCourseIcon();
                if (link == null) {
                    log.info("Course {} has no icon. Skipping...", course.getId());
                    continue;
                }

                if (!link.startsWith("/api/files")) {
                    log.info("Course {} has an icon that does not start with /api/files: {}. Skipping...", course.getId(), link);
                    continue;
                }

                link = updatePrefix(link);
                course.setCourseIcon(link);
                totalUpdated++;
            }

            courseRepository.saveAll(coursesPage.getContent());
            page++;
        }
        while (coursesPage.hasNext());

        log.info("Updated {} courses", totalUpdated);
    }

    @Transactional
    protected void updateUserImageUrls(int batchSize) {
        log.info("Updating user image urls with batch size: {}", batchSize);
        int page = 0;
        long totalUpdated = 0;
        Page<User> usersPage;

        do {
            usersPage = userRepository.findAll(PageRequest.of(page, batchSize));

            for (User user : usersPage.getContent()) {
                String link = user.getImageUrl();
                if (link == null) {
                    log.info("User {} has no image url. Skipping...", user.getId());
                    continue;
                }

                if (!link.startsWith("/api/files")) {
                    log.info("User {} has an image url that does not start with /api/files: {}. Skipping...", user.getId(), link);
                    continue;
                }

                link = updatePrefix(link);
                user.setImageUrl(link);
                totalUpdated++;
            }

            userRepository.saveAll(usersPage.getContent());
            page++;
        }
        while (usersPage.hasNext());

        log.info("Updated {} users", totalUpdated);
    }

    @Transactional
    protected void updateStudentExamAssetPaths(int batchSize) {
        log.info("Updating exam user asset paths with batch size: {}", batchSize);
        int page = 0;
        long totalUpdated = 0;
        Page<ExamUser> examUsersPage;

        do {
            examUsersPage = examUserRepository.findAll(PageRequest.of(page, batchSize));

            for (ExamUser examUser : examUsersPage.getContent()) {
                boolean updated = false;

                String signingImagePath = examUser.getSigningImagePath();
                if (signingImagePath != null && signingImagePath.startsWith("/api/files")) {
                    signingImagePath = updatePrefix(signingImagePath);
                    examUser.setSigningImagePath(signingImagePath);
                    updated = true;
                }
                else if (signingImagePath != null) {
                    log.info("Exam user {} has a signature image path that does not start with /api/files: {}. Skipping...", examUser.getId(), signingImagePath);
                }

                String studentImagePath = examUser.getStudentImagePath();
                if (studentImagePath != null && studentImagePath.startsWith("/api/files")) {
                    studentImagePath = updatePrefix(studentImagePath);
                    examUser.setStudentImagePath(studentImagePath);
                    updated = true;
                }
                else if (studentImagePath != null) {
                    log.info("Exam user {} has a student image that does not start with /api/files: {}. Skipping...", examUser.getId(), studentImagePath);
                }

                if (updated) {
                    totalUpdated++;
                }
            }

            examUserRepository.saveAll(examUsersPage.getContent());
            page++;
        }
        while (examUsersPage.hasNext());

        log.info("Updated {} exam users", totalUpdated);
    }

    @Transactional
    protected void updateFileUploadSubmissionPaths(int batchSize) {
        log.info("Updating file upload submissions with batch size: {}", batchSize);
        int page = 0;
        long totalUpdated = 0;
        Page<FileUploadSubmission> submissionsPage;

        do {
            submissionsPage = fileUploadSubmissionRepository.findAll(PageRequest.of(page, batchSize));

            for (FileUploadSubmission fileUploadSubmission : submissionsPage.getContent()) {
                String link = fileUploadSubmission.getFilePath();
                if (link == null) {
                    log.info("File upload submission {} has no file path. Skipping...", fileUploadSubmission.getId());
                    continue;
                }

                if (!link.startsWith("/api/files")) {
                    log.info("File upload submission {} has a file path that does not start with /api/files: {}. Skipping...", fileUploadSubmission.getId(), link);
                    continue;
                }

                link = updatePrefix(link);
                fileUploadSubmission.setFilePath(link);
                totalUpdated++;
            }

            fileUploadSubmissionRepository.saveAll(submissionsPage.getContent());
            page++;
        }
        while (submissionsPage.hasNext());

        log.info("Updated {} file upload submissions", totalUpdated);
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

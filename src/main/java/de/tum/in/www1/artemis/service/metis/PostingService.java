package de.tum.in.www1.artemis.service.metis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

public abstract class PostingService {

    final CourseRepository courseRepository;

    final ExerciseRepository exerciseRepository;

    final AuthorizationCheckService authorizationCheckService;

    protected PostingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, AuthorizationCheckService authorizationCheckService) {
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Checks if the requesting user is authorized in the course context
     *
     * @param posting posting that is requested
     * @param user    requesting user
     */
    void mayUpdateOrDeletePostingElseThrow(Posting posting, User user) {
        if (!user.getId().equals(posting.getAuthor().getId())) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, posting.getCourse(), user);
        }
    }

    /**
     * Method to (i) compare id of the course belonging to the post with the path variable courseId,
     * and (ii) if the possibly associated exercise is not an exam exercise
     *
     * @param post     post that is checked
     * @param courseId id of the course that is used as path variable
     */
    void preCheckPostValidity(Post post, Long courseId) {
        if (!post.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Post sent in body", getEntityName(), "idnull");
        }

        // do not allow postings for exam exercises
        if (post.getExercise() != null) {
            Long exerciseId = post.getExercise().getId();
            Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            if (exercise.isExamExercise()) {
                throw new BadRequestAlertException("Postings are not allowed for exam exercises", getEntityName(), "400", true);
            }
        }
    }

    /**
     * Compares id of the course belonging to the associated post with the path variable courseId,
     *
     * @param answerPost answer post that is checked
     * @param courseId   id of the course that is used as path variable
     */
    void preCheckAnswerPostValidity(AnswerPost answerPost, Long courseId) {
        if (!answerPost.getPost().getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the associated Post sent in body", getEntityName(), "idnull");
        }
    }

    Course preCheckUserAndCourse(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // check if course has posts enabled
        if (!course.getPostsEnabled()) {
            throw new BadRequestAlertException("Postings are not enabled for this course", getEntityName(), "400", true);
        }

        return course;
    }

    abstract String getEntityName();
}

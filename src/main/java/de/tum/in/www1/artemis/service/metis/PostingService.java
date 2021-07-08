package de.tum.in.www1.artemis.service.metis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

public abstract class PostingService {

    final CourseRepository courseRepository;

    final AuthorizationCheckService authorizationCheckService;

    private final GroupNotificationService groupNotificationService;

    protected PostingService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, GroupNotificationService groupNotificationService) {
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.groupNotificationService = groupNotificationService;
    }

    /**
     * Helper method to send notification to affected groups
     *
     * @param post post that triggered the notification
     */
    void sendNotification(Post post) {
        // notify via exercise
        if (post.getExercise() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewPostForExercise(post);

            // protect Sample Solution, Grading Instructions, etc.
            post.getExercise().filterSensitiveInformation();
        }
        // notify via lecture
        if (post.getLecture() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewPostForLecture(post);
        }
    }

    /**
     * Helper method to check if the requesting user is authorized in the course context
     *
     * @param posting posting that is requested
     * @param user    requesting user
     */
    void mayUpdateOrDeletePostElseThrow(Posting posting, User user) {
        if (!user.getId().equals(posting.getAuthor().getId())) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, posting.getCourse(), user);
        }
    }

    void preCheckUserAndCourse(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // check if course has posts enabled
        if (!course.getPostsEnabled()) {
            throw new BadRequestAlertException("Course with this Id does not have Posts enabled", getEntityName(), "400");
        }
    }

    abstract String getEntityName();

}

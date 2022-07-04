package de.tum.in.www1.artemis.service.metis;

import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.MetisPostDTO;

public abstract class PostingService {

    final CourseRepository courseRepository;

    final ExerciseRepository exerciseRepository;

    final LectureRepository lectureRepository;

    final PostRepository postRepository;

    final AuthorizationCheckService authorizationCheckService;

    private final SimpMessageSendingOperations messagingTemplate;

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    protected PostingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository, PostRepository postRepository,
            AuthorizationCheckService authorizationCheckService, SimpMessageSendingOperations messagingTemplate) {
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
        this.postRepository = postRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts a posting related event in a course under a specific topic via websockets
     *
     * @param postDTO object including the affected post as well as the action
     * @param course  course the posting belongs to
     */
    void broadcastForPost(MetisPostDTO postDTO, Course course) {
        String specificTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX;
        String genericTopicName = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + course.getId();
        if (postDTO.getPost().getExercise() != null) {
            specificTopicName += "exercises/" + postDTO.getPost().getExercise().getId();
            messagingTemplate.convertAndSend(specificTopicName, postDTO);
        }
        else if (postDTO.getPost().getLecture() != null) {
            specificTopicName += "lectures/" + postDTO.getPost().getLecture().getId();
            messagingTemplate.convertAndSend(specificTopicName, postDTO);
        }
        messagingTemplate.convertAndSend(genericTopicName, postDTO);
    }

    /**
     * Checks if the requesting user is authorized in the course context,
     * i.e. a user has to be the author of posting or at least teaching assistant
     *
     * @param posting posting that is requested
     * @param user    requesting user
     * @param course  course the posting belongs to
     */
    void mayUpdateOrDeletePostingElseThrow(Posting posting, User user, Course course) {
        if (!user.getId().equals(posting.getAuthor().getId())) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }

    /**
     * Method to check if the possibly associated exercise is not an exam exercise
     *
     * @param post post that is checked
     */
    void preCheckPostValidity(Post post) {
        // do not allow postings for exam exercises
        if (post.getExercise() != null) {
            Long exerciseId = post.getExercise().getId();
            Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            if (exercise.isExamExercise()) {
                throw new BadRequestAlertException("Postings are not allowed for exam exercises", getEntityName(), "400", true);
            }
        }
    }

    Course preCheckUserAndCourse(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        // user has to be at least student in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // check if the course has posts enabled
        if (!course.getPostsEnabled()) {
            throw new BadRequestAlertException("Postings are not enabled for this course", getEntityName(), "400", true);
        }
        return course;
    }

    abstract String getEntityName();
}

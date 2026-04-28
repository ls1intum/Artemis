package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.iris.service.IrisBotUserService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource;

class IrisAutonomousTutorPipelineIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "autotutorpipeline";

    @Autowired
    private PyrisPipelineService pyrisPipelineService;

    @Autowired
    private PyrisJobService pyrisJobService;

    @Autowired
    private PyrisInternalStatusUpdateResource pyrisInternalStatusUpdateResource;

    @Autowired
    private IrisBotUserService irisBotUserService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    private Course course;

    private Channel channel;

    private User student;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = courseUtilService.createCourse();
        channel = conversationUtilService.createCourseWideChannel(course, "general");
        irisBotUserService.ensureIrisBotUserExists();
        student = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
    }

    private Post createPostInChannel(User author, String content) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(content);
        post.setConversation(channel);
        post.setVisibleForStudents(true);
        return conversationMessageRepository.save(post);
    }

    @Test
    void executeAutonomousTutorPipeline_sendsRequestToPyris() {
        Post post = createPostInChannel(student, "How does inheritance work?");
        var postDTO = new PyrisPostDTO(post);
        var studentDTO = new PyrisUserDTO(student);

        AtomicBoolean pipelineDone = new AtomicBoolean(false);
        AtomicReference<PyrisAutonomousTutorPipelineExecutionDTO> capturedDto = new AtomicReference<>();
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> {
            capturedDto.set(dto);
            pipelineDone.set(true);
        });

        pyrisPipelineService.executeAutonomousTutorPipeline("default", postDTO, course, studentDTO, null, null, null, stages -> {
        });

        await().atMost(java.time.Duration.ofSeconds(5)).until(pipelineDone::get);

        var dto = capturedDto.get();
        assertThat(dto.post().id()).isEqualTo(post.getId());
        assertThat(dto.course()).isNotNull();
        assertThat(dto.user()).isNotNull();
        assertThat(dto.settings()).isNotNull();
        assertThat(dto.programmingExercise()).isNull();
        assertThat(dto.textExercise()).isNull();
        assertThat(dto.lecture()).isNull();
    }

    @Test
    void setAutonomousTutorJobStatus_success() {
        Post post = createPostInChannel(student, "What is polymorphism?");
        String token = pyrisJobService.addAutonomousTutorJob(post.getId(), course.getId());

        var mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, Constants.BEARER_PREFIX + token);

        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO("Polymorphism allows ...", true, 0.9, List.of(), List.of());
        var response = pyrisInternalStatusUpdateResource.setAutonomousTutorJobStatus(token, statusUpdate, mockRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void setAutonomousTutorJobStatus_runIdMismatch_throwsConflict() {
        Post post = createPostInChannel(student, "What is encapsulation?");
        String token = pyrisJobService.addAutonomousTutorJob(post.getId(), course.getId());

        var mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, Constants.BEARER_PREFIX + token);

        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO("Encapsulation hides ...", true, 0.9, List.of(), List.of());

        assertThatThrownBy(() -> pyrisInternalStatusUpdateResource.setAutonomousTutorJobStatus("wrong-run-id", statusUpdate, mockRequest)).isInstanceOf(ConflictException.class);
    }

    @Test
    void autonomousTutorJob_canAccess_matchingCourseId_returnsTrue() {
        var job = new AutonomousTutorJob("token", 1L, course.getId());
        assertThat(job.canAccess(course)).isTrue();
    }

    @Test
    void autonomousTutorJob_canAccess_nonMatchingCourseId_returnsFalse() {
        var job = new AutonomousTutorJob("token", 1L, 99999L);
        assertThat(job.canAccess(course)).isFalse();
    }
}

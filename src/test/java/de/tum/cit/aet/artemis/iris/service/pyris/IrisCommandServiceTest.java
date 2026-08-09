package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisCommandAckDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCommandRequestWebsocketDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisCommandDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisCommandResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Unit tests for {@link IrisCommandService#executeCommand}. Covers the point-out dispatch: the
 * short-circuit guards, the applied path (client navigated -> success + persisted COMMAND marker),
 * the not-applied path (client did nothing -> no marker), generic commands, and the timeout/transport path.
 */
@ExtendWith(MockitoExtension.class)
class IrisCommandServiceTest {

    private static final long COURSE_ID = 3L;

    private static final Long SESSION_ID = 5L;

    private static final long USER_ID = 7L;

    private static final long LECTURE_UNIT_ID = 42L;

    private static final long LECTURE_ID = 27L;

    @Mock
    private IrisCommandCoordinationService coordinationService;

    @Mock
    private IrisWebsocketService irisWebsocketService;

    @Mock
    private IrisChatWebsocketService irisChatWebsocketService;

    @Mock
    private IrisMessageService irisMessageService;

    @Mock
    private IrisSessionRepository irisSessionRepository;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private LectureUnitRepositoryApi lectureUnitRepositoryApi;

    @Mock
    private IrisSession session;

    @Mock
    private LectureUnit lectureUnit;

    private IrisCommandService commandService;

    private ChatJob job;

    @BeforeEach
    void setUp() {
        commandService = new IrisCommandService(coordinationService, irisWebsocketService, irisChatWebsocketService, irisMessageService, irisSessionRepository, userRepository,
                new ObjectMapper(), Optional.of(lectureUnitRepositoryApi));
        job = new ChatJob("job-1", COURSE_ID, SESSION_ID, null, null, null, null);
    }

    /**
     * Builds a point-out command the way Pyris sends it: type plus a parameters map, omitting the parameters that are not set.
     */
    private static PyrisCommandDTO pointOutCommand(@Nullable Long lectureUnitId, @Nullable Integer page) {
        var parameters = new LinkedHashMap<String, JsonNode>();
        if (lectureUnitId != null) {
            parameters.put("lectureUnitId", JsonNodeFactory.instance.numberNode(lectureUnitId));
        }
        if (page != null) {
            parameters.put("page", JsonNodeFactory.instance.numberNode(page));
        }
        return new PyrisCommandDTO("pointOut", parameters);
    }

    private void stubSessionAndUser() {
        when(irisSessionRepository.findByIdElseThrow(SESSION_ID)).thenReturn(session);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getUserId()).thenReturn(USER_ID);
        var user = new User();
        user.setLogin("student1");
        when(userRepository.findByIdElseThrow(USER_ID)).thenReturn(user);
    }

    /**
     * Makes the point-out target resolvable, sitting in the given course — the unit lookup is course-scoped, so a command only gets past it when the two match.
     */
    private void stubLectureUnitInCourse(long courseId) {
        var course = new Course();
        course.setId(courseId);
        var lecture = new Lecture();
        lecture.setId(LECTURE_ID);
        lecture.setCourse(course);
        when(lectureUnitRepositoryApi.findAllByIdsWithLecture(List.of(LECTURE_UNIT_ID))).thenReturn(List.of(lectureUnit));
        when(lectureUnit.getLecture()).thenReturn(lecture);
    }

    @Test
    void executeCommand_appliedNavigatesPersistsMarkerAndReturnsSuccess() {
        stubSessionAndUser();
        stubLectureUnitInCourse(COURSE_ID);
        when(coordinationService.register(anyString(), eq("student1"))).thenReturn(CompletableFuture.completedFuture(new IrisCommandAckDTO("corr", true)));
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.COMMAND))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = commandService.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, 3));

        assertThat(result.applied()).isTrue();
        verify(irisWebsocketService).send(eq("student1"), anyString(), any());
        verify(irisMessageService).saveMessage(any(), eq(session), eq(IrisMessageSender.COMMAND));
        verify(irisChatWebsocketService).sendMessage(eq(session), any(), isNull(), isNull());
    }

    @Test
    void executeCommand_persistsMarkerInTheSameShapeTheCommandArrivedIn() {
        stubSessionAndUser();
        stubLectureUnitInCourse(COURSE_ID);
        when(coordinationService.register(anyString(), eq("student1"))).thenReturn(CompletableFuture.completedFuture(new IrisCommandAckDTO("corr", true)));
        when(lectureUnit.getName()).thenReturn("Sorting");
        var savedMarker = ArgumentCaptor.forClass(IrisMessage.class);
        when(irisMessageService.saveMessage(savedMarker.capture(), eq(session), eq(IrisMessageSender.COMMAND))).thenAnswer(invocation -> invocation.getArgument(0));

        commandService.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, 3));

        // The marker mirrors the command's {type, parameters} shape so history readers parse it like a command, with
        // what only Artemis can resolve added to the parameters: the unit's name and the lecture it belongs to. The
        // lecture is what a click on the chip navigates by later, when the chat may sit in a different one entirely.
        var marker = ((IrisJsonMessageContent) savedMarker.getValue().getContent().getFirst()).getJsonNode();
        assertThat(marker.get("type").asText()).isEqualTo("pointOut");
        assertThat(marker.get("parameters").get("lectureUnitId").asLong()).isEqualTo(LECTURE_UNIT_ID);
        assertThat(marker.get("parameters").get("page").asInt()).isEqualTo(3);
        assertThat(marker.get("parameters").get("lectureUnitName").asText()).isEqualTo("Sorting");
        assertThat(marker.get("parameters").get("lectureId").asLong()).isEqualTo(LECTURE_ID);
        assertThat(marker.has("lectureUnitId")).isFalse();
    }

    @Test
    void commandDtos_serializeTheWayPyrisAndTheClientExpect() throws Exception {
        var mapper = new ObjectMapper();
        var parameters = Map.<String, JsonNode>of("lectureUnitId", JsonNodeFactory.instance.numberNode(LECTURE_UNIT_ID));

        // Pyris requires "applied" in the response body; NON_EMPTY must not drop the primitive false.
        assertThat(mapper.writeValueAsString(PyrisCommandResultDTO.notApplied())).isEqualTo("{\"applied\":false}");
        assertThat(mapper.writeValueAsString(new IrisCommandRequestWebsocketDTO("corr-1", "pointOut", parameters, null)))
                .isEqualTo("{\"correlationId\":\"corr-1\",\"type\":\"pointOut\",\"parameters\":{\"lectureUnitId\":42}}");
    }

    @Test
    void commandRequest_isAddressedToTheTabTheChatRunWasStartedFrom() {
        // Delivery is per user, so every tab receives the request; naming the originating tab is what keeps the other
        // tabs from answering for it. Event-triggered runs have no originating tab: the request then names none and
        // any tab may carry it out.
        stubSessionAndUser();
        stubLectureUnitInCourse(COURSE_ID);
        when(coordinationService.register(anyString(), eq("student1"))).thenReturn(CompletableFuture.completedFuture(new IrisCommandAckDTO("corr", true)));
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.COMMAND))).thenAnswer(invocation -> invocation.getArgument(0));
        var jobStartedFromTab = new ChatJob("job-2", COURSE_ID, SESSION_ID, null, null, null, null, "tab-7");
        var payload = ArgumentCaptor.forClass(Object.class);

        commandService.executeCommand(jobStartedFromTab, pointOutCommand(LECTURE_UNIT_ID, 3));
        commandService.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, 3));

        verify(irisWebsocketService, times(2)).send(eq("student1"), anyString(), payload.capture());
        assertThat(((IrisCommandRequestWebsocketDTO) payload.getAllValues().getFirst()).targetClientId()).isEqualTo("tab-7");
        assertThat(((IrisCommandRequestWebsocketDTO) payload.getAllValues().getLast()).targetClientId()).isNull();
    }

    @Test
    void executeCommand_unsupportedCommandTypeIsDroppedWithoutContactingTheClient() throws Exception {
        var command = new ObjectMapper().readValue("""
                {
                    "type": "highlightTerm",
                    "parameters": {
                        "term": "quicksort"
                    }
                }
                """, PyrisCommandDTO.class);

        var result = commandService.executeCommand(job, command);

        // Point-out is the only defined command type. Anything else must not reach the client at all, so the
        // pipeline learns "not applied" immediately instead of waiting out the ack timeout for nobody.
        assertThat(result.applied()).isFalse();
        verify(coordinationService, never()).register(anyString(), anyString());
        verify(irisWebsocketService, never()).send(any(), any(), any());
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    void executeCommand_notAppliedDoesNotPersistMarker() {
        stubSessionAndUser();
        stubLectureUnitInCourse(COURSE_ID);
        when(coordinationService.register(anyString(), eq("student1"))).thenReturn(CompletableFuture.completedFuture(new IrisCommandAckDTO("corr", false)));

        var result = commandService.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, 3));

        assertThat(result.applied()).isFalse();
        verify(irisWebsocketService).send(eq("student1"), anyString(), any());
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    void executeCommand_timeoutIsReportedAsNotApplied() {
        stubSessionAndUser();
        stubLectureUnitInCourse(COURSE_ID);
        when(coordinationService.register(anyString(), eq("student1"))).thenReturn(CompletableFuture.failedFuture(new TimeoutException("no ack")));

        var result = commandService.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, 3));

        assertThat(result.applied()).isFalse();
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }

    @Test
    void executeCommand_incompletePointOutShortCircuitsWithoutContactingClient() {
        // Neither a command without a target unit nor one without a position in it can be carried out by anyone.
        assertThat(commandService.executeCommand(job, pointOutCommand(null, 3)).applied()).isFalse();
        assertThat(commandService.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, null)).applied()).isFalse();

        verify(coordinationService, never()).register(anyString(), anyString());
        verify(irisWebsocketService, never()).send(any(), any(), any());
    }

    @Test
    void executeCommand_pointOutIntoAnUnresolvableUnitShortCircuitsWithoutContactingClient() {
        // The id is model-generated, so none of these three is a transport problem to be waited out: no client could
        // navigate to any of them, and forwarding them would stall the pipeline for the full ack timeout for nobody.

        // A unit outside the chat's course. Above all its name must never reach this chat's history.
        stubLectureUnitInCourse(COURSE_ID + 1);
        assertThat(commandService.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, 3)).applied()).isFalse();

        // An id that exists nowhere.
        when(lectureUnitRepositoryApi.findAllByIdsWithLecture(List.of(LECTURE_UNIT_ID))).thenReturn(List.of());
        assertThat(commandService.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, 3)).applied()).isFalse();

        // No lecture module at all, so there are no units to point into.
        var serviceWithoutLectures = new IrisCommandService(coordinationService, irisWebsocketService, irisChatWebsocketService, irisMessageService, irisSessionRepository,
                userRepository, new ObjectMapper(), Optional.empty());
        assertThat(serviceWithoutLectures.executeCommand(job, pointOutCommand(LECTURE_UNIT_ID, 3)).applied()).isFalse();

        verify(coordinationService, never()).register(anyString(), anyString());
        verify(irisWebsocketService, never()).send(any(), any(), any());
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }
}

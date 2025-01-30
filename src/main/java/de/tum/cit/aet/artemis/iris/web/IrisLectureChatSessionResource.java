package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisLectureChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisLectureChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/iris/lecture-chat/")

public class IrisLectureChatSessionResource {

    private final UserRepository userRepository;

    private final IrisSessionService irisSessionService;

    private final IrisSettingsService irisSettingsService;

    private final LectureRepository lectureRepository;

    private final IrisLectureChatSessionService irisLectureChatSessionService;

    private final IrisLectureChatSessionRepository irisLectureChatSessionRepository;

    private final AuthorizationCheckService authorizationCheckService;

    protected IrisLectureChatSessionResource(UserRepository userRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService,
            LectureRepository lectureRepository, IrisLectureChatSessionService irisLectureChatSessionService, IrisLectureChatSessionRepository irisLectureChatSessionRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.lectureRepository = lectureRepository;
        this.irisLectureChatSessionService = irisLectureChatSessionService;
        this.irisLectureChatSessionRepository = irisLectureChatSessionRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET api/iris/lecture-chat/{lectureId}/sessions/current: Retrieve the current iris session for the lecture
     *
     * @param lectureId of the lecture
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the lecture or {@code 404 (Not Found)} if no session exists
     */
    @PostMapping("{lectureId}/sessions/current")
    public ResponseEntity<IrisLectureChatSession> getCurrentSessionOrCreateIfNotExists(@PathVariable Long lectureId) throws URISyntaxException {
        var lecture = lectureRepository.findByIdElseThrow(lectureId);
        validateLecture(lecture);

        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.LECTURE_CHAT, lecture.getCourse());
        var sessionOptional = irisLectureChatSessionRepository.findLatestSessionsByLectureIdAndUserIdWithMessages(lecture.getId(), user.getId(), Pageable.ofSize(1)).stream()
                .findFirst();
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();
            irisSessionService.checkHasAccessToIrisSession(session, user);
            return ResponseEntity.ok(session);
        }

        return createSessionForLecture(lectureId);
    }

    /**
     * POST api/iris/lecture-chat/{lectureId}/session: Create a new iris session for a lecture and user.
     * If there already exists an iris session for the lecture and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param lectureId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the lecture
     */
    @PostMapping("{lectureId}/sessions")
    public ResponseEntity<IrisLectureChatSession> createSessionForLecture(@PathVariable Long lectureId) throws URISyntaxException {
        var lecture = lectureRepository.findByIdElseThrow(lectureId);
        validateLecture(lecture);

        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.LECTURE_CHAT, lecture.getCourse());
        user.hasAcceptedIrisElseThrow();

        var session = irisLectureChatSessionRepository.save(new IrisLectureChatSession(lecture, user));
        var uriString = "/api/iris/sessions/" + session.getId();

        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    /**
     * GET api/iris/lecture-chat/{lectureId}/sessions: Retrieve all Iris Sessions for the lecture
     *
     * @param lectureId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{lectureId}/sessions")
    public ResponseEntity<List<IrisLectureChatSession>> getAllSessions(@PathVariable Long lectureId) {
        var lecture = lectureRepository.findByIdElseThrow(lectureId);
        validateLecture(lecture);

        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.LECTURE_CHAT, lecture.getCourse());
        user.hasAcceptedIrisElseThrow();

        var sessions = irisLectureChatSessionRepository.findByLectureIdAndUserIdOrderByCreationDateDesc(lecture.getId(), user.getId());
        // Access check might not even be necessary here -> see comments in hasAccess method
        var filteredSessions = sessions.stream().filter(session -> irisLectureChatSessionService.hasAccess(user, session)).toList();
        return ResponseEntity.ok(filteredSessions);
    }

    private static void validateLecture(Lecture lecture) {
        if (!lecture.isVisibleToStudents()) {
            throw new ConflictException("The lecture is not visible to students yet", "Iris", "irisLecture");
        }
    }
}

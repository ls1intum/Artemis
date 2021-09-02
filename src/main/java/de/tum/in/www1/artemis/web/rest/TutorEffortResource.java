package de.tum.in.www1.artemis.web.rest;

import static java.lang.Math.toIntExact;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.statistics.tutor.effort.TutorEffort;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing TutorEffortResource.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('INSTRUCTOR')")
public class TutorEffortResource {

    private final Logger log = LoggerFactory.getLogger(TutorEffortResource.class);

    private final ExerciseRepository exerciseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    private final int thresholdMinutes = 5;

    public TutorEffortResource(AuthorizationCheckService authorizationCheckService, ExerciseRepository exerciseRepository, UserRepository userRepository,
            TextAssessmentEventRepository textAssessmentEventRepository) {
        this.exerciseRepository = exerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.textAssessmentEventRepository = textAssessmentEventRepository;
    }

    /**
     * Calculates and retrieves tutor effort and returns a list for the respective course and exercise
     * @param courseId the id of the course to query for
     * @param exerciseId the id of the exercise to query for
     * @return list of TutorEffort objects
     */
    @PostMapping("/courses/{courseId}/exercises/{exerciseId}/tutor-effort")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TutorEffort>> calculateTutorEfforts(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        log.debug("tutor-effort with argument[s] course = {}, exercise = {}", courseId, exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);

        Map<Long, Integer> submissionsPerTutor = textAssessmentEventRepository.getAssessedSubmissionCountPerTutor(course.getId(), exerciseId);
        List<TextAssessmentEvent> listOfEvents = textAssessmentEventRepository.findAllNonEmptyEvents(courseId, exerciseId);
        List<TutorEffort> tutorEffortList = new ArrayList<>();

        Map<Long, List<TextAssessmentEvent>> newMap = groupByUserId(listOfEvents);
        if (newMap.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        newMap.forEach((currentUserId, currentUserEvents) -> {
            if (currentUserEvents != null) {
                TutorEffort effort = createTutorEffortWithInformation(currentUserId, currentUserEvents, submissionsPerTutor.get(currentUserId));
                tutorEffortList.add(effort);
            }
        });
        return ResponseEntity.ok().body(tutorEffortList);
    }

    /**
     * Takes in parameters and sets respective properties on a new TutorEffort object
     * @param userId the id of the user to set
     * @param events the events of the respective user
     * @param submissions the number of submissions the tutor assessed
     * @return a TutorEffort object with all the data set
     */
    private TutorEffort createTutorEffortWithInformation(Long userId, List<TextAssessmentEvent> events, int submissions) {
        TutorEffort effort = new TutorEffort();
        effort.setUserId(userId);
        effort.setCourseId(events.get(0).getCourseId());
        effort.setExerciseId(events.get(0).getTextExerciseId());
        effort.setTotalTimeSpentMinutes(calculateTutorOverallTimeSpent(events));
        effort.setNumberOfSubmissionsAssessed(submissions);
        return effort;
    }

    /**
     * Traverses over tutorEvents (which contain timestamps). Calculates time spent between timestamps
     * assuming timestamps are not further than THRESHOLD_MINUTES away from each other.
     * @param tutorEvents events to be analysed
     * @return the number of minutes spent
     */
    private int calculateTutorOverallTimeSpent(List<TextAssessmentEvent> tutorEvents) {
        int timeSeconds = 0;
        for (int i = 0; i < tutorEvents.size(); i++) {
            TextAssessmentEvent current = tutorEvents.get(i);
            try {
                // access next element & catch out of bounds if end of the list.
                TextAssessmentEvent next = tutorEvents.get(i + 1);
                int diffInSeconds = getDateDiffInSeconds(Date.from(current.getTimestamp()), Date.from(next.getTimestamp()));
                if (diffInSeconds <= thresholdMinutes * 60) {
                    timeSeconds += diffInSeconds;
                }
            }
            catch (IndexOutOfBoundsException ignored) {
            }
        }
        return timeSeconds / 60;
    }

    /**
     * Groups assessment events by user id into a map
     * @param events the events to query from
     * @return a map with key representing user id and value representing respective list of events for the user
     */
    private Map<Long, List<TextAssessmentEvent>> groupByUserId(List<TextAssessmentEvent> events) {
        Map<Long, List<TextAssessmentEvent>> map = new HashMap<>();

        events.forEach((event) -> {
            Long cUserId = event.getUserId();
            var cEvents = map.get(cUserId);
            // key, value pair doesn't exist, initialize empty list
            if (cEvents == null) {
                cEvents = new ArrayList<>();
            }
            // key, value pair exists, append to value list, a new element (pass by reference, map.put redundant)
            cEvents.add(event);
            map.putIfAbsent(cUserId, cEvents);
        });
        return map;
    }

    /**
     * Takes in two dates and returns difference between them in seconds
     * @param first first date to compare
     * @param second second date to compare
     * @return difference in seconds
     */
    public int getDateDiffInSeconds(Date first, Date second) {
        long diffInMilliseconds = Math.abs(first.getTime() - second.getTime());
        try {
            return toIntExact(TimeUnit.SECONDS.convert(diffInMilliseconds, TimeUnit.MILLISECONDS));
        }
        catch (ArithmeticException exception) {
            // discard if faulty date found. Treat as outlier
            return thresholdMinutes * 60 + 1;
        }
    }
}

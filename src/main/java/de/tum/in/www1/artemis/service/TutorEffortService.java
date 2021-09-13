package de.tum.in.www1.artemis.service;

import static java.lang.Math.toIntExact;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.statistics.tutor.effort.TutorEffort;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;

/**
 * Contains business logic needed to calculate tutor efforts.
 */
@Service
public class TutorEffortService {

    private static final int THRESHOLD_MINUTES = 5;

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    public TutorEffortService(TextAssessmentEventRepository textAssessmentEventRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
    }

    /**
     * Takes in list of events and submissions per tutor and builds the resulting tutor effort list by relying on the rest
     * of the business logic. First assessments are grouped by user id, then the new tutor effort list is built
     * from the resulting grouping
     * @param courseId id of the course to calculate for
     * @param exerciseId id of the exercise to calculate for
     * @return
     */
    public List<TutorEffort> buildTutorEffortList(Long courseId, Long exerciseId) {
        Map<Long, Integer> submissionsPerTutor = textAssessmentEventRepository.getAssessedSubmissionCountPerTutor(courseId, exerciseId);
        List<TextAssessmentEvent> listOfEvents = textAssessmentEventRepository.findAllNonEmptyEvents(courseId, exerciseId);

        List<TutorEffort> tutorEffortList = new ArrayList<>();
        Map<Long, List<TextAssessmentEvent>> newMap = groupByUserId(listOfEvents);
        if (newMap.isEmpty()) {
            return tutorEffortList;
        }
        newMap.forEach((currentUserId, currentUserEvents) -> {
            if (currentUserEvents != null) {
                TutorEffort effort = createTutorEffortWithInformation(currentUserId, currentUserEvents, submissionsPerTutor.get(currentUserId));
                tutorEffortList.add(effort);
            }
        });
        return tutorEffortList;
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
        int index = 0;
        // avoid index out of bounds by incrementing index for accessing tutorEvents list elements
        while (index + 1 < tutorEvents.size()) {
            TextAssessmentEvent current = tutorEvents.get(index);
            // access next element
            TextAssessmentEvent next = tutorEvents.get(index + 1);
            int diffInSeconds = getDateDiffInSeconds(Date.from(current.getTimestamp()), Date.from(next.getTimestamp()));
            if (diffInSeconds <= THRESHOLD_MINUTES * 60) {
                timeSeconds += diffInSeconds;
            }
            index++;
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
            Long currentUserId = event.getUserId();
            // if key, value pair doesn't exist, initialize empty list
            var cEvents = map.getOrDefault(currentUserId, new ArrayList<>());
            // append a new element to value list
            cEvents.add(event);
            map.putIfAbsent(currentUserId, cEvents);
        });
        return map;
    }

    /**
     * Takes in two dates and returns difference between them in seconds
     * @param first first date to compare
     * @param second second date to compare
     * @return difference in seconds
     */
    private int getDateDiffInSeconds(Date first, Date second) {
        long diffInMilliseconds = Math.abs(first.getTime() - second.getTime());
        try {
            return toIntExact(TimeUnit.SECONDS.convert(diffInMilliseconds, TimeUnit.MILLISECONDS));
        }
        catch (ArithmeticException exception) {
            // discard if faulty date found. Treat as outlier
            return THRESHOLD_MINUTES * 60 + 1;
        }
    }
}

package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.dto.CourseRoleCountDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeCourseDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Service for calculating exercise-related metrics using optimized query patterns.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseMetricsService {

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    public ExerciseMetricsService(ExerciseRepository exerciseRepository, UserRepository userRepository) {
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Count active students in exercises with release dates between minDate and maxDate, grouped by exercise type.
     * Uses an optimized three-query approach:
     * 1. Get distinct (exerciseType, courseId) pairs for exercises in the date range (fast index scan)
     * 2. Get IDs of users who have submitted since activeSince (single scan of participations/submissions)
     * 3. Count students per course via UserCourseRole, filtering to only active user IDs (simple IN clause)
     *
     * @param minDate     the minimum release date
     * @param maxDate     the maximum release date
     * @param activeSince timestamp defining when a user is considered active
     * @return a set of ExerciseTypeMetricsEntry, one for each exercise type
     */
    public Set<ExerciseTypeMetricsEntry> countActiveStudentsInExercisesWithReleaseDateBetweenGroupByExerciseType(ZonedDateTime minDate, ZonedDateTime maxDate,
            ZonedDateTime activeSince) {
        // Step 1: Get exercise types and their course ids
        Set<ExerciseTypeCourseDTO> typeGroups = exerciseRepository.findExerciseTypesAndCourseIdsWithReleaseDateBetween(minDate, maxDate);
        // Step 2 & 3: Aggregate active students by exercise type
        return aggregateActiveStudentsByExerciseType(typeGroups, activeSince);
    }

    /**
     * Count active students in exercises with due dates between minDate and maxDate, grouped by exercise type.
     * Uses an optimized three-query approach:
     * 1. Get distinct (exerciseType, courseId) pairs for exercises in the date range (fast index scan)
     * 2. Get IDs of users who have submitted since activeSince (single scan of participations/submissions)
     * 3. Count students per course via UserCourseRole, filtering to only active user IDs (simple IN clause)
     *
     * @param minDate     the minimum due date
     * @param maxDate     the maximum due date
     * @param activeSince timestamp defining when a user is considered active
     * @return a set of ExerciseTypeMetricsEntry, one for each exercise type
     */
    public Set<ExerciseTypeMetricsEntry> countActiveStudentsInExercisesWithDueDateBetweenGroupByExerciseType(ZonedDateTime minDate, ZonedDateTime maxDate,
            ZonedDateTime activeSince) {
        // Step 1: Get exercise types and their course ids
        Set<ExerciseTypeCourseDTO> typeGroups = exerciseRepository.findExerciseTypesAndCourseIdsWithDueDateBetween(minDate, maxDate);
        // Step 2 & 3: Aggregate active students by exercise type
        return aggregateActiveStudentsByExerciseType(typeGroups, activeSince);
    }

    /**
     * Aggregate active students by exercise type using a UserCourseRole-based three-query approach.
     *
     * @param typeGroups  the set of (exerciseType, courseId) pairs
     * @param activeSince timestamp defining when a user is considered active
     * @return a set of ExerciseTypeMetricsEntry aggregated by exercise type
     */
    private Set<ExerciseTypeMetricsEntry> aggregateActiveStudentsByExerciseType(Set<ExerciseTypeCourseDTO> typeGroups, ZonedDateTime activeSince) {
        if (typeGroups.isEmpty()) {
            return new HashSet<>();
        }

        // Build a map from courseId to exercise types (a course can belong to multiple exercise types)
        Map<Long, Set<ExerciseType>> courseToTypes = new HashMap<>();
        for (ExerciseTypeCourseDTO dto : typeGroups) {
            courseToTypes.computeIfAbsent(dto.courseId(), _ -> new HashSet<>()).add(dto.exerciseType());
        }

        // Step 2: Get all active user IDs (users who have submitted since activeSince)
        Set<Long> activeUserIds = userRepository.findActiveUserIdsSince(activeSince);

        if (activeUserIds.isEmpty()) {
            // No active users, return zero counts for each exercise type
            return typeGroups.stream().map(ExerciseTypeCourseDTO::exerciseType).distinct().map(type -> new ExerciseTypeMetricsEntry(type.getExerciseClass(), 0L))
                    .collect(Collectors.toSet());
        }

        // Step 3: Count active students per course via UserCourseRole, filtering to active user IDs
        Set<Long> allCourseIds = courseToTypes.keySet();
        List<CourseRoleCountDTO> activeStudentCounts = userRepository.countStudentsByCourseIdsAndUserIds(allCourseIds, activeUserIds);

        // Create a map from courseId to count for easy lookup
        Map<Long, Long> courseToCount = activeStudentCounts.stream().collect(Collectors.toMap(CourseRoleCountDTO::courseId, CourseRoleCountDTO::count));

        // Step 4: Aggregate by exercise type
        // For each exercise type, sum up the active students from all associated courses
        Map<ExerciseType, Long> typeToCount = new HashMap<>();
        for (ExerciseTypeCourseDTO dto : typeGroups) {
            long count = courseToCount.getOrDefault(dto.courseId(), 0L);
            typeToCount.merge(dto.exerciseType(), count, Long::sum);
        }

        // Convert to result set — ExerciseTypeMetricsEntry takes the exercise Class, not the enum
        return typeToCount.entrySet().stream().map(entry -> new ExerciseTypeMetricsEntry(entry.getKey().getExerciseClass(), entry.getValue())).collect(Collectors.toSet());
    }
}

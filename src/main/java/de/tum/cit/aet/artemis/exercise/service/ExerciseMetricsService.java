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

import de.tum.cit.aet.artemis.core.dto.StudentGroupCountDTO;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeStudentGroupDTO;
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
     * Uses an optimized three-query approach to avoid expensive MEMBER OF and EXISTS clauses.
     *
     * @param minDate     the minimum release date
     * @param maxDate     the maximum release date
     * @param activeSince timestamp defining when a user is considered active
     * @return a set of ExerciseTypeMetricsEntry, one for each exercise type
     */
    public Set<ExerciseTypeMetricsEntry> countActiveStudentsInExercisesWithReleaseDateBetweenGroupByExerciseType(ZonedDateTime minDate, ZonedDateTime maxDate,
            ZonedDateTime activeSince) {
        // Step 1: Get exercise types and their student groups
        Set<ExerciseTypeStudentGroupDTO> typeGroups = exerciseRepository.findExerciseTypesAndStudentGroupsWithReleaseDateBetween(minDate, maxDate);
        // Step 2 & 3: Aggregate active students by exercise type
        return aggregateActiveStudentsByExerciseType(typeGroups, activeSince);
    }

    /**
     * Count active students in exercises with due dates between minDate and maxDate, grouped by exercise type.
     * Uses an optimized three-query approach to avoid expensive MEMBER OF and EXISTS clauses:
     * 1. Get distinct exercise types and their student group names (fast)
     * 2. Get IDs of users who have submitted since activeSince (single scan of participations/submissions)
     * 3. Count users by group, filtering to only active user IDs (simple IN clause)
     *
     * @param minDate     the minimum due date
     * @param maxDate     the maximum due date
     * @param activeSince timestamp defining when a user is considered active
     * @return a set of ExerciseTypeMetricsEntry, one for each exercise type
     */
    public Set<ExerciseTypeMetricsEntry> countActiveStudentsInExercisesWithDueDateBetweenGroupByExerciseType(ZonedDateTime minDate, ZonedDateTime maxDate,
            ZonedDateTime activeSince) {
        // Step 1: Get exercise types and their student groups
        Set<ExerciseTypeStudentGroupDTO> typeGroups = exerciseRepository.findExerciseTypesAndStudentGroupsWithDueDateBetween(minDate, maxDate);
        // Step 2 & 3: Aggregate active students by exercise type
        return aggregateActiveStudentsByExerciseType(typeGroups, activeSince);
    }

    /**
     * Aggregate active students by exercise type using the optimized three-query approach.
     *
     * @param typeGroups  the set of exercise types and their student groups
     * @param activeSince timestamp defining when a user is considered active
     * @return a set of ExerciseTypeMetricsEntry aggregated by exercise type
     */
    private Set<ExerciseTypeMetricsEntry> aggregateActiveStudentsByExerciseType(Set<ExerciseTypeStudentGroupDTO> typeGroups, ZonedDateTime activeSince) {
        if (typeGroups.isEmpty()) {
            return new HashSet<>();
        }

        // Build a map from student group to exercise types (a group can belong to multiple exercise types)
        Map<String, Set<ExerciseType>> groupToTypes = new HashMap<>();
        for (ExerciseTypeStudentGroupDTO dto : typeGroups) {
            groupToTypes.computeIfAbsent(dto.studentGroupName(), _ -> new HashSet<>()).add(dto.exerciseType());
        }

        // Step 2: Get all active user IDs (users who have submitted since activeSince)
        Set<Long> activeUserIds = userRepository.findActiveUserIdsSince(activeSince);

        if (activeUserIds.isEmpty()) {
            // No active users, return empty counts for each exercise type
            return typeGroups.stream().map(ExerciseTypeStudentGroupDTO::exerciseType).distinct().map(type -> new ExerciseTypeMetricsEntry(type.getExerciseClass(), 0L))
                    .collect(Collectors.toSet());
        }

        // Step 3: Count active users by student group
        Set<String> allStudentGroups = groupToTypes.keySet();
        List<StudentGroupCountDTO> activeStudentCounts = userRepository.countUsersByStudentGroupNamesAndUserIds(allStudentGroups, activeUserIds);

        // Create a map from group name to count for easy lookup
        Map<String, Long> groupToCount = activeStudentCounts.stream().collect(Collectors.toMap(StudentGroupCountDTO::studentGroupName, StudentGroupCountDTO::count));

        // Step 4: Aggregate by exercise type
        // For each exercise type, sum up the active students from all associated groups
        Map<ExerciseType, Long> typeToCount = new HashMap<>();

        for (ExerciseTypeStudentGroupDTO dto : typeGroups) {
            long count = groupToCount.getOrDefault(dto.studentGroupName(), 0L);
            typeToCount.merge(dto.exerciseType(), count, Long::sum);
        }

        // Convert to result set - need to convert ExerciseType back to Class for ExerciseTypeMetricsEntry
        return typeToCount.entrySet().stream().map(entry -> new ExerciseTypeMetricsEntry(entry.getKey().getExerciseClass(), entry.getValue())).collect(Collectors.toSet());
    }
}

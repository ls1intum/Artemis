package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.dto.PublicUsageStatisticsDTO;
import de.tum.cit.aet.artemis.admin.dto.StudentSubmissionCountDTO;
import de.tum.cit.aet.artemis.admin.dto.UsageSemesterDTO;
import de.tum.cit.aet.artemis.admin.repository.PublicUsageStatisticsRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry;

/**
 * Computes the anonymous usage numbers shown on the public usage dashboard, from existing data only.
 * <p>
 * Definitions: a <em>real course</em> is no test course and has at least {@link #MIN_STUDENTS_PER_COURSE} students. A course belongs to the
 * semester in which it starts. A <em>student</em> is a unique user enrolled as student in at least one real course of the semester. An
 * <em>active student</em> is a student with at least one submission in an exercise of these courses, including their exams. Test users, deleted users and
 * submissions that instructors or the system create for a student are never counted. A semester with fewer than {@link #MIN_COURSES_PER_SEMESTER}
 * real courses is not published at all, because its numbers would describe a single course.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class PublicUsageStatisticsService {

    /**
     * Name of the distributed cache holding the result. Its entries expire after one hour, see {@code CacheManagerConfiguration}.
     */
    public static final String CACHE_NAME = "publicUsageStatistics";

    /**
     * Courses with fewer students are not published, so that no small group can be identified.
     */
    static final int MIN_STUDENTS_PER_COURSE = 5;

    /**
     * Semesters with fewer courses are not published, so that the numbers of a semester never describe one identifiable course.
     */
    static final int MIN_COURSES_PER_SEMESTER = 3;

    static final int NUMBER_OF_SEMESTERS = 4;

    private final PublicUsageStatisticsRepository publicUsageStatisticsRepository;

    public PublicUsageStatisticsService(PublicUsageStatisticsRepository publicUsageStatisticsRepository) {
        this.publicUsageStatisticsRepository = publicUsageStatisticsRepository;
    }

    /**
     * Returns the usage statistics of the last {@value #NUMBER_OF_SEMESTERS} semesters that have enough courses to be published. The result is cached
     * for one hour on all nodes, the computation scans the submissions of two years and must not run per request.
     *
     * @return the statistics, newest semester first
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "'usage'", sync = true)
    public PublicUsageStatisticsDTO getUsageStatistics() {
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        List<UsageSemesterDTO> semesters = UsageSemester.lastSemesters(LocalDate.ofInstant(now, zone), NUMBER_OF_SEMESTERS).stream()
                .map(semester -> computeSemester(semester, zone)).flatMap(Optional::stream).toList();
        return new PublicUsageStatisticsDTO(now, semesters);
    }

    private Optional<UsageSemesterDTO> computeSemester(UsageSemester semester, ZoneId zone) {
        Set<Long> courseIds = publicUsageStatisticsRepository.findRealCourseIds(semester.start(zone), semester.end(zone), MIN_STUDENTS_PER_COURSE);
        if (courseIds.size() < MIN_COURSES_PER_SEMESTER) {
            return Optional.empty();
        }
        Set<Long> studentIds = publicUsageStatisticsRepository.findStudentIds(courseIds);
        List<StudentSubmissionCountDTO> submissionCounts = publicUsageStatisticsRepository
                .countSubmissionsByStudent(publicUsageStatisticsRepository.findExerciseIdsOfCourses(courseIds));
        // Only submissions of counted students and of teams are published, so the numbers never include staff, test users or deleted users.
        var countedSubmissions = submissionCounts.stream().filter(count -> count.studentId() == null || studentIds.contains(count.studentId())).toList();
        long activeStudents = countedSubmissions.stream().filter(count -> count.studentId() != null).count();
        long submissions = countedSubmissions.stream().mapToLong(StudentSubmissionCountDTO::submissions).sum();
        long lectures = publicUsageStatisticsRepository.countLectures(courseIds);
        return Optional.of(new UsageSemesterDTO(semester.label(), courseIds.size(), studentIds.size(), activeStudents, submissions, lectures,
                exercisesByType(publicUsageStatisticsRepository.countExercisesByType(courseIds))));
    }

    /**
     * @param entries the counts per exercise type, types without exercises are missing
     * @return a map with every exercise type, so that the client never has to guess which keys exist
     */
    private static Map<String, Long> exercisesByType(Set<ExerciseTypeMetricsEntry> entries) {
        var counts = new EnumMap<ExerciseType, Long>(ExerciseType.class);
        for (ExerciseType type : ExerciseType.values()) {
            counts.put(type, 0L);
        }
        entries.forEach(entry -> counts.put(entry.exerciseType(), entry.value()));
        var result = new LinkedHashMap<String, Long>();
        counts.forEach((type, count) -> result.put(type.getValue(), count));
        return result;
    }
}

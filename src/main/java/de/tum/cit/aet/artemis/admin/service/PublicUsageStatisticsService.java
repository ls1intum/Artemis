package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.dto.BuildUsageDTO;
import de.tum.cit.aet.artemis.admin.dto.CommunicationUsageDTO;
import de.tum.cit.aet.artemis.admin.dto.PublicUsageStatisticsDTO;
import de.tum.cit.aet.artemis.admin.dto.StudentSubmissionCountDTO;
import de.tum.cit.aet.artemis.admin.dto.UsageSemesterDTO;
import de.tum.cit.aet.artemis.admin.repository.PublicUsageStatisticsRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyUsageStatisticsApi;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.exam.api.ExamUsageStatisticsApi;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry;
import de.tum.cit.aet.artemis.iris.api.IrisUsageStatisticsApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupUsageStatisticsApi;

/**
 * Computes the anonymous usage numbers shown on the public usage dashboard, from existing data only.
 * <p>
 * Definitions: a <em>real course</em> is no test course and has at least {@link #MIN_STUDENTS_PER_COURSE} students. A course belongs to the
 * semester in which it starts. A <em>student</em> is a unique user enrolled as student in at least one real course of the semester. An
 * <em>active student</em> is a student with at least one submission in an exercise of these courses, including their exams. Test users, deleted users and
 * submissions that instructors or the system create for a student are never counted. A semester with fewer than {@link #MIN_COURSES_PER_SEMESTER}
 * real courses is not published at all, because its numbers would describe a single course.
 * <p>
 * The activity numbers (finished builds, posts, replies, reactions, published FAQs, Iris messages and tutorial groups) are totals over the real
 * courses of the semester, whenever they happened. They include what staff and the system did in these courses, for example builds of template and
 * solution repositories and answers of Iris; only the replies that students cannot see (unverified drafts of Iris) are left out. Build jobs are
 * deleted after their retention period, so the builds of semesters that started before it are not published.
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

    /**
     * Exercises per submission query, far below the bind parameter limit of PostgreSQL (32767).
     */
    static final int EXERCISE_IDS_PER_QUERY = 5000;

    private final PublicUsageStatisticsRepository publicUsageStatisticsRepository;

    private final ProfileService profileService;

    private final Optional<IrisUsageStatisticsApi> irisUsageStatisticsApi;

    private final Optional<TutorialGroupUsageStatisticsApi> tutorialGroupUsageStatisticsApi;

    private final Optional<ExamUsageStatisticsApi> examUsageStatisticsApi;

    private final Optional<CompetencyUsageStatisticsApi> competencyUsageStatisticsApi;

    private final int buildJobRetentionDays;

    /**
     * @param profileService        tells whether builds run on local CI, the only build system that records build jobs
     * @param buildJobRetentionDays the number of days after which build jobs are deleted. Builds of a semester that starts earlier are incomplete and are not published.
     */
    public PublicUsageStatisticsService(PublicUsageStatisticsRepository publicUsageStatisticsRepository, ProfileService profileService,
            Optional<IrisUsageStatisticsApi> irisUsageStatisticsApi, Optional<TutorialGroupUsageStatisticsApi> tutorialGroupUsageStatisticsApi,
            Optional<ExamUsageStatisticsApi> examUsageStatisticsApi, Optional<CompetencyUsageStatisticsApi> competencyUsageStatisticsApi,
            @Value("${artemis.continuous-integration.build-job.retention-period:365}") int buildJobRetentionDays) {
        this.publicUsageStatisticsRepository = publicUsageStatisticsRepository;
        this.profileService = profileService;
        this.irisUsageStatisticsApi = irisUsageStatisticsApi;
        this.tutorialGroupUsageStatisticsApi = tutorialGroupUsageStatisticsApi;
        this.examUsageStatisticsApi = examUsageStatisticsApi;
        this.competencyUsageStatisticsApi = competencyUsageStatisticsApi;
        this.buildJobRetentionDays = buildJobRetentionDays;
    }

    /**
     * Returns the usage statistics of the last {@value #NUMBER_OF_SEMESTERS} semesters that have enough courses to be published. The result is cached
     * for one hour on all nodes, the computation scans the submissions of two years and must not run per request.
     *
     * @return the statistics, newest semester first
     */
    // The key names the shape of the cached records. A change of the shape is therefore compatible without bumping DistributedDataSchema.VERSION: a node only
    // reads the key of its own shape and never an entry that a build with another shape wrote. Increase the key whenever a record component is added, removed
    // or retyped in any record reachable from PublicUsageStatisticsDTO, including BuildUsageDTO and CommunicationUsageDTO.
    @Cacheable(cacheNames = CACHE_NAME, key = "'usage-4'", sync = true)
    public PublicUsageStatisticsDTO getUsageStatistics() {
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        List<UsageSemesterDTO> semesters = UsageSemester.lastSemesters(LocalDate.ofInstant(now, zone), NUMBER_OF_SEMESTERS).stream()
                .map(semester -> computeSemester(semester, zone, now)).flatMap(Optional::stream).toList();
        return new PublicUsageStatisticsDTO(now, semesters);
    }

    private Optional<UsageSemesterDTO> computeSemester(UsageSemester semester, ZoneId zone, Instant now) {
        Set<Long> courseIds = publicUsageStatisticsRepository.findRealCourseIds(semester.start(zone), semester.end(zone), MIN_STUDENTS_PER_COURSE);
        if (courseIds.size() < MIN_COURSES_PER_SEMESTER) {
            return Optional.empty();
        }
        Set<Long> studentIds = publicUsageStatisticsRepository.findStudentIds(courseIds);
        List<StudentSubmissionCountDTO> submissionCounts = countSubmissionsByStudent(publicUsageStatisticsRepository.findExerciseIdsOfCourses(courseIds));
        // Only submissions of counted students and of teams are published, so the numbers never include staff, test users or deleted users.
        var countedSubmissions = submissionCounts.stream().filter(count -> count.studentId() == null || studentIds.contains(count.studentId())).toList();
        long activeStudents = countedSubmissions.stream().filter(count -> count.studentId() != null).count();
        long submissions = countedSubmissions.stream().mapToLong(StudentSubmissionCountDTO::submissions).sum();
        long lectures = publicUsageStatisticsRepository.countLectures(courseIds);
        Long irisMessages = irisUsageStatisticsApi.map(api -> api.countMessagesInCourses(courseIds)).orElse(null);
        Long tutorialGroups = tutorialGroupUsageStatisticsApi.map(api -> api.countTutorialGroups(courseIds)).orElse(null);
        Long exams = examUsageStatisticsApi.map(api -> api.countRealExams(courseIds)).orElse(null);
        Long examRegistrations = examUsageStatisticsApi.map(api -> api.countRealExamRegistrations(courseIds)).orElse(null);
        Long competencies = competencyUsageStatisticsApi.map(api -> api.countCompetencies(courseIds)).orElse(null);
        return Optional.of(new UsageSemesterDTO(semester.label(), courseIds.size(), studentIds.size(), publicUsageStatisticsRepository.countStudentEnrollments(courseIds),
                activeStudents, submissions, lectures, exercisesByType(publicUsageStatisticsRepository.countExercisesByType(courseIds)), buildUsage(courseIds, semester, zone, now),
                communicationUsage(courseIds), irisMessages, tutorialGroups, exams, examRegistrations, competencies));
    }

    /**
     * Build jobs are deleted after the retention period, so the builds of a semester that started before it are incomplete. Showing them would make
     * the builds of older semesters look smaller than they were, which is why they are not published at all.
     * <p>
     * Only local CI records build jobs, so an instance that builds with Jenkins publishes no build numbers instead of a misleading zero.
     *
     * @return the build numbers, null if builds do not run on local CI or the semester started before the retention period of build jobs
     */
    private BuildUsageDTO buildUsage(Set<Long> courseIds, UsageSemester semester, ZoneId zone, Instant now) {
        if (!profileService.isLocalCIActive()) {
            return null;
        }
        boolean allBuildJobsStillExist = !semester.start(zone).toInstant().isBefore(now.minus(Duration.ofDays(buildJobRetentionDays)));
        return allBuildJobsStillExist ? publicUsageStatisticsRepository.summarizeFinishedBuildJobs(courseIds) : null;
    }

    private CommunicationUsageDTO communicationUsage(Set<Long> courseIds) {
        long reactions = publicUsageStatisticsRepository.countReactionsOnPosts(courseIds) + publicUsageStatisticsRepository.countReactionsOnAnswerPosts(courseIds);
        return new CommunicationUsageDTO(publicUsageStatisticsRepository.countPosts(courseIds), publicUsageStatisticsRepository.countAnswerPosts(courseIds), reactions,
                publicUsageStatisticsRepository.countAcceptedFaqs(courseIds));
    }

    /**
     * Counts the submissions in chunks of exercises, because every exercise id of an {@code IN} list is a bind parameter and a large instance can
     * exceed the limit of the database driver. Each submission belongs to exactly one exercise, so summing the counts of the chunks per student is exact.
     * The entry without student id (team submissions) is merged like any other.
     *
     * @param exerciseIds the exercises of the real courses
     * @return one entry per student, plus one entry without student id for team submissions
     */
    private List<StudentSubmissionCountDTO> countSubmissionsByStudent(Set<Long> exerciseIds) {
        Map<Long, Long> submissionsByStudent = new HashMap<>();
        for (List<Long> chunk : ListUtils.partition(new ArrayList<>(exerciseIds), EXERCISE_IDS_PER_QUERY)) {
            publicUsageStatisticsRepository.countSubmissionsByStudent(new HashSet<>(chunk))
                    .forEach(count -> submissionsByStudent.merge(count.studentId(), count.submissions(), Long::sum));
        }
        return submissionsByStudent.entrySet().stream().map(entry -> new StudentSubmissionCountDTO(entry.getKey(), entry.getValue())).toList();
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

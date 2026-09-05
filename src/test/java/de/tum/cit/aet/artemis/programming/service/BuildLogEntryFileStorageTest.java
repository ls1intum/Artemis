package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseNamesDTO;
import de.tum.cit.aet.artemis.programming.repository.BuildLogEntryRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Unit tests for the build logs a build job leaves on disk.
 * <p>
 * Build logs are the only record of why a build failed, and they are kept outside the database, under
 * {@code <buildLogsPath>/<course>/<exercise>/<buildJobId>.log}. Two things therefore have to hold without anyone
 * watching: a log written for a build job has to be found again by the endpoint that serves it, and the nightly cleanup
 * has to remove what has expired without taking anything else with it.
 */
@ExtendWith(MockitoExtension.class)
class BuildLogEntryFileStorageTest {

    private static final String BUILD_JOB_ID = "build-job-1";

    private static final String COURSE_SHORT_NAME = "course1";

    private static final String EXERCISE_SHORT_NAME = "exercise1";

    private static final int EXPIRY_DAYS = 7;

    /** Boxed on purpose: the repository declares both findByIdElseThrow(long) and the inherited findByIdElseThrow(ID), and the service calls the latter. */
    private static final Long EXERCISE_ID = 1L;

    @Mock
    private BuildLogEntryRepository buildLogEntryRepository;

    @Mock
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private BuildJobTestRepository buildJobRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @TempDir
    Path buildLogsPath;

    private BuildLogEntryService buildLogEntryService;

    @BeforeEach
    void setUp() {
        buildLogEntryService = new BuildLogEntryService(buildLogEntryRepository, programmingSubmissionRepository, profileService, buildJobRepository,
                programmingExerciseRepository);
        ReflectionTestUtils.setField(buildLogEntryService, "buildLogsPath", buildLogsPath);
        ReflectionTestUtils.setField(buildLogEntryService, "expiryDays", EXPIRY_DAYS);
    }

    private static ProgrammingExercise exercise() {
        Course course = new Course();
        course.setShortName(COURSE_SHORT_NAME);
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setShortName(EXERCISE_SHORT_NAME);
        exercise.setCourse(course);
        exercise.setId(EXERCISE_ID);
        return exercise;
    }

    private Path exerciseLogsPath() {
        return buildLogsPath.resolve(COURSE_SHORT_NAME).resolve(EXERCISE_SHORT_NAME);
    }

    private Path writeLogFile(Path path, String content) throws IOException {
        FileUtils.write(path.toFile(), content, StandardCharsets.UTF_8);
        return path;
    }

    private void ageFile(Path file, int days) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(days, ChronoUnit.DAYS)));
    }

    @Test
    void saveBuildLogsToFile_writesOneLineWithTheTimestampPerEntry() {
        ZonedDateTime firstEntryTime = ZonedDateTime.parse("2200-01-10T12:00:00Z");
        List<BuildLogDTO> buildLogs = List.of(new BuildLogDTO(firstEntryTime, "compiling\n"), new BuildLogDTO(firstEntryTime.plusSeconds(1), "done\n"));

        buildLogEntryService.saveBuildLogsToFile(buildLogs, BUILD_JOB_ID, exercise());

        Path logFile = exerciseLogsPath().resolve(BUILD_JOB_ID + ".log");
        assertThat(logFile).as("the log is stored under the course and the exercise it belongs to").isRegularFile();
        // The timestamp is what makes a build log readable, so it is written in front of every line rather than only once.
        assertThat(logFile).content(StandardCharsets.UTF_8).contains(firstEntryTime + "\tcompiling").contains(firstEntryTime.plusSeconds(1) + "\tdone");
    }

    @Test
    void saveBuildLogsToFile_forTheFirstBuildOfAnExercise_createsTheDirectoryItNeeds() {
        assertThat(exerciseLogsPath()).as("nothing exists for this exercise yet").doesNotExist();

        buildLogEntryService.saveBuildLogsToFile(List.of(new BuildLogDTO(ZonedDateTime.parse("2200-01-10T12:00:00Z"), "first build\n")), BUILD_JOB_ID, exercise());

        assertThat(exerciseLogsPath().resolve(BUILD_JOB_ID + ".log")).isRegularFile();
    }

    @Test
    void retrieveBuildLogsFromFileForBuildJob_findsTheLogTheBuildWrote() throws Exception {
        buildLogEntryService.saveBuildLogsToFile(List.of(new BuildLogDTO(ZonedDateTime.parse("2200-01-10T12:00:00Z"), "compiling\n")), BUILD_JOB_ID, exercise());
        mockBuildJobLookup();

        var resource = buildLogEntryService.retrieveBuildLogsFromFileForBuildJob(BUILD_JOB_ID);

        assertThat(resource).as("the log written for the build job is served").isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.getFile().toPath()).isEqualTo(exerciseLogsPath().resolve(BUILD_JOB_ID + ".log"));
    }

    @Test
    void retrieveBuildLogsFromFileForBuildJob_fallsBackToTheFlatLayoutOfOlderBuilds() throws Exception {
        // Build logs used to be written directly into the root, before they were grouped by course and exercise. Those logs have to stay retrievable.
        writeLogFile(buildLogsPath.resolve(BUILD_JOB_ID + ".log"), "an old build log");
        mockBuildJobLookup();

        var resource = buildLogEntryService.retrieveBuildLogsFromFileForBuildJob(BUILD_JOB_ID);

        assertThat(resource).as("a log written before the layout changed is still found").isNotNull();
        assertThat(resource.getFile().toPath()).isEqualTo(buildLogsPath.resolve(BUILD_JOB_ID + ".log"));
    }

    @Test
    void retrieveBuildLogsFromFileForBuildJob_withoutALogFile_returnsNothing() {
        mockBuildJobLookup();

        assertThat(buildLogEntryService.retrieveBuildLogsFromFileForBuildJob(BUILD_JOB_ID)).as("a build job without a log file yields no resource").isNull();
    }

    @Test
    void retrieveBuildLogsFromFileForBuildJob_forABuildJobIdThatWalksThePath_isRejected() {
        // The build job id reaches this method from the request path, so it must never be resolved against the file system as it is.
        for (String maliciousBuildJobId : List.of("../../etc/passwd", "..", "sub/build-job", "sub\\build-job")) {
            assertThatExceptionOfType(IllegalArgumentException.class).as("build job id '%s' must be rejected", maliciousBuildJobId)
                    .isThrownBy(() -> buildLogEntryService.retrieveBuildLogsFromFileForBuildJob(maliciousBuildJobId)).withMessageContaining("Invalid build job ID");
        }
    }

    @Test
    void buildJobHasLogFile_reportsBothLayoutsAndNothingElse() throws Exception {
        var names = new ProgrammingExerciseNamesDTO(EXERCISE_SHORT_NAME, COURSE_SHORT_NAME);
        assertThat(buildLogEntryService.buildJobHasLogFile(BUILD_JOB_ID, names)).as("no log has been written yet").isFalse();

        writeLogFile(exerciseLogsPath().resolve(BUILD_JOB_ID + ".log"), "a build log");
        assertThat(buildLogEntryService.buildJobHasLogFile(BUILD_JOB_ID, names)).as("a log in the exercise directory is found").isTrue();

        FileUtils.deleteDirectory(buildLogsPath.resolve(COURSE_SHORT_NAME).toFile());
        writeLogFile(buildLogsPath.resolve(BUILD_JOB_ID + ".log"), "an old build log");
        assertThat(buildLogEntryService.buildJobHasLogFile(BUILD_JOB_ID, names)).as("a log in the old flat layout is found as well").isTrue();
    }

    @Test
    void deleteOldBuildLogsFiles_whenSchedulingIsInactive_deletesNothing() throws Exception {
        // Only the node that runs the schedule may clean up; otherwise every node in the cluster would delete the same files.
        when(profileService.isSchedulingActive()).thenReturn(false);
        Path expiredLog = writeLogFile(exerciseLogsPath().resolve(BUILD_JOB_ID + ".log"), "an expired build log");
        ageFile(expiredLog, EXPIRY_DAYS + 1);

        buildLogEntryService.deleteOldBuildLogsFiles();

        assertThat(expiredLog).as("a node that does not run the schedule leaves the files alone").exists();
    }

    @Test
    void deleteOldBuildLogsFiles_removesTheExpiredLogsAndTheDirectoriesTheyEmptied() throws Exception {
        when(profileService.isSchedulingActive()).thenReturn(true);
        Path expiredLog = writeLogFile(exerciseLogsPath().resolve("expired.log"), "an expired build log");
        ageFile(expiredLog, EXPIRY_DAYS + 1);
        Path recentLog = writeLogFile(buildLogsPath.resolve("course2").resolve("exercise2").resolve("recent.log"), "a recent build log");
        ageFile(recentLog, EXPIRY_DAYS - 1);

        buildLogEntryService.deleteOldBuildLogsFiles();

        assertThat(expiredLog).as("a log older than the retention period is deleted").doesNotExist();
        assertThat(recentLog).as("a log inside the retention period is kept").exists();
        // An exercise that stopped producing builds would otherwise leave an empty directory behind for every term.
        assertThat(exerciseLogsPath()).as("the directory the deleted log left empty is removed").doesNotExist();
        assertThat(buildLogsPath.resolve(COURSE_SHORT_NAME)).as("the course directory is removed once its last exercise is gone").doesNotExist();
        assertThat(buildLogsPath).as("the root of the build logs is never removed").isDirectory();
    }

    @Test
    void deleteOldBuildLogsFiles_keepsADirectoryThatStillHoldsALog() throws Exception {
        when(profileService.isSchedulingActive()).thenReturn(true);
        Path expiredLog = writeLogFile(exerciseLogsPath().resolve("expired.log"), "an expired build log");
        ageFile(expiredLog, EXPIRY_DAYS + 1);
        Path recentLog = writeLogFile(exerciseLogsPath().resolve("recent.log"), "a recent build log");
        ageFile(recentLog, 1);

        buildLogEntryService.deleteOldBuildLogsFiles();

        assertThat(expiredLog).doesNotExist();
        assertThat(recentLog).as("the log that is still within the retention period survives").exists();
        assertThat(exerciseLogsPath()).as("a directory that still holds a log is kept").isDirectory();
    }

    private void mockBuildJobLookup() {
        BuildJob buildJob = new BuildJob();
        buildJob.setExerciseId(EXERCISE_ID);
        when(buildJobRepository.findByBuildJobIdElseThrow(BUILD_JOB_ID)).thenReturn(buildJob);
        when(programmingExerciseRepository.findByIdElseThrow(EXERCISE_ID)).thenReturn(exercise());
    }
}

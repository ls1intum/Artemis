package de.tum.cit.aet.artemis.demo.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi.DemoUsers;
import de.tum.cit.aet.artemis.atlas.api.AtlasDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService.DemoExercises;
import de.tum.cit.aet.artemis.lecture.api.LectureDemoApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Seeds the content of the demo course around its exercises, see {@link DemoDataSeedingService}.
 */
@Service
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class DemoCourseContentSeedingService {

    private final Optional<LectureDemoApi> lectureDemoApi;

    private final Optional<AtlasDemoApi> atlasDemoApi;

    public DemoCourseContentSeedingService(Optional<LectureDemoApi> lectureDemoApi, Optional<AtlasDemoApi> atlasDemoApi) {
        this.lectureDemoApi = lectureDemoApi;
        this.atlasDemoApi = atlasDemoApi;
    }

    /**
     * Seeds the content of the demo course that builds on its exercises.
     *
     * @param course    the demo course.
     * @param users     the demo users.
     * @param exercises the demo exercises by topic.
     */
    void seed(Course course, DemoUsers users, DemoExercises exercises) {
        List<LectureUnit> lectureUnits = DemoAreas.seed("lectures", () -> lectureDemoApi.map(api -> api.createDemo(course)).orElse(List.of()), List.of());
        DemoAreas.seed("competencies", () -> atlasDemoApi.ifPresent(api -> api.createDemo(course, lectureUnits)));
    }
}

package de.tum.cit.aet.artemis.demo.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi.DemoUsers;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.atlas.api.AtlasDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService.DemoExercises;
import de.tum.cit.aet.artemis.lecture.api.LectureDemoApi;
import de.tum.cit.aet.artemis.lecture.api.dtos.DemoLectures;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupDemoApi;

/**
 * Seeds the content of the demo course around its exercises, see {@link DemoDataSeedingService}.
 */
@Service
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class DemoCourseContentSeedingService {

    /**
     * What the later areas continue with when the lectures are not seeded.
     */
    private static final DemoLectures NO_LECTURES = new DemoLectures(List.of(), List.of(), List.of());

    private final Optional<LectureDemoApi> lectureDemoApi;

    private final Optional<AtlasDemoApi> atlasDemoApi;

    private final AssessmentDemoApi assessmentDemoApi;

    private final Optional<TutorialGroupDemoApi> tutorialGroupDemoApi;

    public DemoCourseContentSeedingService(Optional<LectureDemoApi> lectureDemoApi, Optional<AtlasDemoApi> atlasDemoApi, AssessmentDemoApi assessmentDemoApi,
            Optional<TutorialGroupDemoApi> tutorialGroupDemoApi) {
        this.lectureDemoApi = lectureDemoApi;
        this.atlasDemoApi = atlasDemoApi;
        this.assessmentDemoApi = assessmentDemoApi;
        this.tutorialGroupDemoApi = tutorialGroupDemoApi;
    }

    /**
     * Seeds the content of the demo course that builds on its exercises.
     *
     * @param course    the demo course.
     * @param users     the demo users.
     * @param exercises the demo exercises by topic.
     */
    void seed(Course course, DemoUsers users, DemoExercises exercises) {
        DemoLectures lectures = DemoAreas.seed("lectures",
                () -> lectureDemoApi.map(api -> api.createDemo(course, exercises.architecture(), exercises.algorithms(), exercises.modeling())).orElse(NO_LECTURES), NO_LECTURES);
        // An exercise unit is linked to competencies through its exercise, see CourseCompetency#prePersistOrUpdate, so only the other units are handed on.
        DemoAreas.seed("competencies",
                () -> atlasDemoApi.ifPresent(api -> api.createDemo(course, lectures.architecture().stream().filter(unit -> !(unit instanceof ExerciseUnit)).toList())));
        DemoAreas.seed("grading scale", () -> assessmentDemoApi.createDemoGradingScale(course));
        DemoAreas.seed("tutorial groups", () -> tutorialGroupDemoApi.ifPresent(api -> api.createDemo(course, users.tutor(), users.students())));
    }
}

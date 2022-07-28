import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { IndividualLearningGoalProgress, IndividualLectureUnitProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { Component, Input } from '@angular/core';
import { CourseLearningGoalsComponent } from 'app/overview/course-learning-goals/course-learning-goals.component';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { cloneDeep } from 'lodash-es';
import * as Sentry from '@sentry/browser';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Course } from 'app/entities/course.model';

@Component({ selector: 'jhi-learning-goal-card', template: '<div><ng-content></ng-content></div>' })
class LearningGoalCardStubComponent {
    @Input() learningGoal: LearningGoal;
    @Input() learningGoalProgress: IndividualLearningGoalProgress;
    @Input() isPrerequisite: Boolean;
    @Input() displayOnly: Boolean;
}

class MockActivatedRoute {
    parent: any;
    params: any;

    constructor(options: { parent?: any; params?: any }) {
        this.parent = options.parent;
        this.params = options.params;
    }
}

const mockActivatedRoute = new MockActivatedRoute({
    parent: new MockActivatedRoute({
        parent: new MockActivatedRoute({
            params: of({ courseId: '1' }),
        }),
    }),
});
describe('CourseLearningGoals', () => {
    let courseLearningGoalsComponentFixture: ComponentFixture<CourseLearningGoalsComponent>;
    let courseLearningGoalsComponent: CourseLearningGoalsComponent;
    let learningGoalService: LearningGoalService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CourseLearningGoalsComponent, LearningGoalCardStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(AlertService),
                MockProvider(CourseScoreCalculationService),
                MockProvider(LearningGoalService),
                MockProvider(AccountService),
                {
                    provide: ActivatedRoute,
                    useValue: mockActivatedRoute,
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                courseLearningGoalsComponentFixture = TestBed.createComponent(CourseLearningGoalsComponent);
                courseLearningGoalsComponent = courseLearningGoalsComponentFixture.componentInstance;
                learningGoalService = TestBed.inject(LearningGoalService);
                const accountService = TestBed.inject(AccountService);
                const user = new User();
                user.login = 'testUser';
                jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue(user);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        courseLearningGoalsComponentFixture.detectChanges();
        expect(courseLearningGoalsComponent).toBeDefined();
        expect(courseLearningGoalsComponent.courseId).toBe(1);
    });

    it('should load progress for each learning goal in a given course', () => {
        const courseCalculationService = TestBed.inject(CourseScoreCalculationService);
        const learningGoal = new LearningGoal();
        const textUnit = new TextUnit();
        learningGoal.id = 1;
        learningGoal.description = 'Petierunt uti sibi concilium totius';
        learningGoal.lectureUnits = [textUnit];

        // Mock a course that was already fetched in another component
        const course = new Course();
        course.id = 1;
        course.learningGoals = [learningGoal];
        course.prerequisites = [learningGoal];
        courseCalculationService.setCourses([course]);
        const getCourseSpy = jest.spyOn(courseCalculationService, 'getCourse').mockReturnValue(course);

        const learningUnitProgress = new IndividualLectureUnitProgress();
        learningUnitProgress.lectureUnitId = textUnit.id!;
        learningUnitProgress.totalPointsAchievableByStudentsInLectureUnit = 10;
        const learningGoalProgress = new IndividualLearningGoalProgress();
        learningGoalProgress.learningGoalId = learningGoal.id!;
        learningGoalProgress.learningGoalTitle = learningGoal.title!;
        learningGoalProgress.pointsAchievedByStudentInLearningGoal = 5;
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 10;
        learningGoalProgress.progressInLectureUnits = [learningUnitProgress];

        const learningGoalProgressResponse: HttpResponse<IndividualLearningGoalProgress> = new HttpResponse({
            body: learningGoalProgress,
            status: 200,
        });

        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse');
        const getProgressSpy = jest.spyOn(learningGoalService, 'getProgress');
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressResponse)); // when useParticipantScoreTable = false
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressResponse)); // when useParticipantScoreTable = true

        courseLearningGoalsComponentFixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getCourseSpy).toHaveBeenCalledWith(1);
        expect(courseLearningGoalsComponent.course).toEqual(course);
        expect(courseLearningGoalsComponent.learningGoals).toEqual([learningGoal]);
        expect(getAllForCourseSpy).not.toHaveBeenCalled(); // do not load learning goals again as already fetched
        expect(getProgressSpy).toHaveBeenCalledTimes(2);
        expect(getProgressSpy).toHaveBeenNthCalledWith(1, 1, 1, false);
        expect(getProgressSpy).toHaveBeenNthCalledWith(2, 1, 1, true);
        expect(courseLearningGoalsComponent.learningGoalIdToLearningGoalProgress.get(1)).toEqual(learningGoalProgress);
    });

    it('should load prerequisites and learning goals (with associated progress) and display a card for each of them', () => {
        const learningGoal = new LearningGoal();
        const textUnit = new TextUnit();
        learningGoal.id = 1;
        learningGoal.description = 'test';
        learningGoal.lectureUnits = [textUnit];
        const learningUnitProgress = new IndividualLectureUnitProgress();
        learningUnitProgress.lectureUnitId = 1;
        learningUnitProgress.totalPointsAchievableByStudentsInLectureUnit = 10;
        const learningGoalProgress = new IndividualLearningGoalProgress();
        learningGoalProgress.learningGoalId = 1;
        learningGoalProgress.learningGoalTitle = 'test';
        learningGoalProgress.pointsAchievedByStudentInLearningGoal = 5;
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 10;
        learningGoalProgress.progressInLectureUnits = [learningUnitProgress];

        const prerequisitesOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [new LearningGoal()],
            status: 200,
        });
        const learningGoalsOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [learningGoal, new LearningGoal()],
            status: 200,
        });
        const learningGoalProgressResponse: HttpResponse<IndividualLearningGoalProgress> = new HttpResponse({
            body: learningGoalProgress,
            status: 200,
        });

        const learningGoalProgressParticipantScores = cloneDeep(learningGoalProgress);
        learningGoalProgressParticipantScores.pointsAchievedByStudentInLearningGoal = 0;
        const learningGoalProgressParticipantScoreResponse: HttpResponse<IndividualLearningGoalProgress> = new HttpResponse({
            body: learningGoalProgressParticipantScores,
            status: 200,
        });

        const getAllPrerequisitesForCourseSpy = jest.spyOn(learningGoalService, 'getAllPrerequisitesForCourse').mockReturnValue(of(prerequisitesOfCourseResponse));
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(learningGoalsOfCourseResponse));
        const getProgressSpy = jest.spyOn(learningGoalService, 'getProgress');
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressResponse)); // when useParticipantScoreTable = false
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressResponse)); // when useParticipantScoreTable = false
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressParticipantScoreResponse)); // when useParticipantScoreTable = true
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressParticipantScoreResponse)); // when useParticipantScoreTable = true

        const captureExceptionSpy = jest.spyOn(Sentry, 'captureException');

        courseLearningGoalsComponentFixture.detectChanges();

        const learningGoalCards = courseLearningGoalsComponentFixture.debugElement.queryAll(By.directive(LearningGoalCardStubComponent));
        expect(learningGoalCards).toHaveLength(3); // 1 prerequisite and 2 learning goals
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(getProgressSpy).toHaveBeenCalledTimes(4);
        expect(courseLearningGoalsComponent.learningGoals).toHaveLength(2);
        expect(courseLearningGoalsComponent.learningGoalIdToLearningGoalProgressUsingParticipantScoresTables.has(1)).toBeTrue();
        expect(courseLearningGoalsComponent.learningGoalIdToLearningGoalProgress.has(1)).toBeTrue();
        expect(captureExceptionSpy).toHaveBeenCalledOnce();
    });
});

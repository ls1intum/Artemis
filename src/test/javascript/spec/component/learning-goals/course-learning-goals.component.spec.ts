import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';

import { LearningGoalCardStubComponent } from './learning-goal-card-stub.component';
import { ArtemisTestModule } from '../../test.module';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { AlertService } from 'app/core/util/alert.service';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { Course } from 'app/entities/course.model';
import { LearningGoal, LearningGoalProgress } from 'app/entities/learningGoal.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { CourseLearningGoalsComponent } from 'app/overview/course-learning-goals/course-learning-goals.component';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
            imports: [ArtemisTestModule, HttpClientTestingModule],
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
        learningGoal.userProgress = [{ progress: 70, confidence: 45 } as LearningGoalProgress];
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

        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse');

        courseLearningGoalsComponentFixture.detectChanges();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getCourseSpy).toHaveBeenCalledWith(1);
        expect(courseLearningGoalsComponent.course).toEqual(course);
        expect(courseLearningGoalsComponent.learningGoals).toEqual([learningGoal]);
        expect(getAllForCourseSpy).not.toHaveBeenCalled(); // do not load learning goals again as already fetched
    });

    it('should load prerequisites and learning goals (with associated progress) and display a card for each of them', () => {
        const learningGoal = new LearningGoal();
        const textUnit = new TextUnit();
        learningGoal.id = 1;
        learningGoal.description = 'test';
        learningGoal.lectureUnits = [textUnit];
        learningGoal.userProgress = [{ progress: 70, confidence: 45 } as LearningGoalProgress];

        const prerequisitesOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [new LearningGoal()],
            status: 200,
        });
        const learningGoalsOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [learningGoal, new LearningGoal()],
            status: 200,
        });

        const getAllPrerequisitesForCourseSpy = jest.spyOn(learningGoalService, 'getAllPrerequisitesForCourse').mockReturnValue(of(prerequisitesOfCourseResponse));
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(learningGoalsOfCourseResponse));

        courseLearningGoalsComponent.isCollapsed = false;
        courseLearningGoalsComponentFixture.detectChanges();

        const learningGoalCards = courseLearningGoalsComponentFixture.debugElement.queryAll(By.directive(LearningGoalCardStubComponent));
        expect(learningGoalCards).toHaveLength(3); // 1 prerequisite and 2 learning goals
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(courseLearningGoalsComponent.learningGoals).toHaveLength(2);
    });
});

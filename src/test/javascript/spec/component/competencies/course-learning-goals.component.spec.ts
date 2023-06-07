import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { Competency, CompetencyProgress } from 'app/entities/competency.model';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetenciesComponent } from 'app/overview/course-competencies/course-competencies.component';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ArtemisTestModule } from '../../test.module';
import { CompetencyCardStubComponent } from './competency-card-stub.component';

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
    let courseLearningGoalsComponentFixture: ComponentFixture<CourseCompetenciesComponent>;
    let courseLearningGoalsComponent: CourseCompetenciesComponent;
    let learningGoalService: CompetencyService;
    const mockCourseStorageService = {
        getCourse: () => {},
        setCourses: () => {},
        subscribeToCourseUpdates: () => of(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            declarations: [CourseCompetenciesComponent, CompetencyCardStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(AlertService),
                { provide: CourseStorageService, useValue: mockCourseStorageService },
                MockProvider(CompetencyService),
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
                courseLearningGoalsComponentFixture = TestBed.createComponent(CourseCompetenciesComponent);
                courseLearningGoalsComponent = courseLearningGoalsComponentFixture.componentInstance;
                learningGoalService = TestBed.inject(CompetencyService);
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
        const courseStorageService = TestBed.inject(CourseStorageService);
        const learningGoal = new Competency();
        learningGoal.userProgress = [{ progress: 70, confidence: 45 } as CompetencyProgress];
        const textUnit = new TextUnit();
        learningGoal.id = 1;
        learningGoal.description = 'Petierunt uti sibi concilium totius';
        learningGoal.lectureUnits = [textUnit];

        // Mock a course that was already fetched in another component
        const course = new Course();
        course.id = 1;
        course.competencies = [learningGoal];
        course.prerequisites = [learningGoal];
        courseStorageService.setCourses([course]);
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);

        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse');

        courseLearningGoalsComponentFixture.detectChanges();

        expect(getCourseStub).toHaveBeenCalledOnce();
        expect(getCourseStub).toHaveBeenCalledWith(1);
        expect(courseLearningGoalsComponent.course).toEqual(course);
        expect(courseLearningGoalsComponent.competencies).toEqual([learningGoal]);
        expect(getAllForCourseSpy).not.toHaveBeenCalled(); // do not load competencies again as already fetched
    });

    it('should load prerequisites and learning goals (with associated progress) and display a card for each of them', () => {
        const learningGoal = new Competency();
        const textUnit = new TextUnit();
        learningGoal.id = 1;
        learningGoal.description = 'test';
        learningGoal.lectureUnits = [textUnit];
        learningGoal.userProgress = [{ progress: 70, confidence: 45 } as CompetencyProgress];

        const prerequisitesOfCourseResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: [new Competency()],
            status: 200,
        });
        const learningGoalsOfCourseResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: [learningGoal, new Competency()],
            status: 200,
        });

        const getAllPrerequisitesForCourseSpy = jest.spyOn(learningGoalService, 'getAllPrerequisitesForCourse').mockReturnValue(of(prerequisitesOfCourseResponse));
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(learningGoalsOfCourseResponse));

        courseLearningGoalsComponent.isCollapsed = false;
        courseLearningGoalsComponentFixture.detectChanges();

        const learningGoalCards = courseLearningGoalsComponentFixture.debugElement.queryAll(By.directive(CompetencyCardStubComponent));
        expect(learningGoalCards).toHaveLength(3); // 1 prerequisite and 2 competencies
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(courseLearningGoalsComponent.competencies).toHaveLength(2);
    });
});

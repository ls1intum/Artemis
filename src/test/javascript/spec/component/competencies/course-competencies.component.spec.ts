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
describe('CourseCompetencies', () => {
    let courseCompetenciesComponentFixture: ComponentFixture<CourseCompetenciesComponent>;
    let courseCompetenciesComponent: CourseCompetenciesComponent;
    let competencyService: CompetencyService;
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
                courseCompetenciesComponentFixture = TestBed.createComponent(CourseCompetenciesComponent);
                courseCompetenciesComponent = courseCompetenciesComponentFixture.componentInstance;
                competencyService = TestBed.inject(CompetencyService);
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
        courseCompetenciesComponentFixture.detectChanges();
        expect(courseCompetenciesComponent).toBeDefined();
        expect(courseCompetenciesComponent.courseId).toBe(1);
    });

    it('should load progress for each competency in a given course', () => {
        const courseStorageService = TestBed.inject(CourseStorageService);
        const competency = new Competency();
        competency.userProgress = [{ progress: 70, confidence: 45 } as CompetencyProgress];
        const textUnit = new TextUnit();
        competency.id = 1;
        competency.description = 'Petierunt uti sibi concilium totius';
        competency.lectureUnits = [textUnit];

        // Mock a course that was already fetched in another component
        const course = new Course();
        course.id = 1;
        course.competencies = [competency];
        course.prerequisites = [competency];
        courseStorageService.setCourses([course]);
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);

        const getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse');

        courseCompetenciesComponentFixture.detectChanges();

        expect(getCourseStub).toHaveBeenCalledOnce();
        expect(getCourseStub).toHaveBeenCalledWith(1);
        expect(courseCompetenciesComponent.course).toEqual(course);
        expect(courseCompetenciesComponent.competencies).toEqual([competency]);
        expect(getAllForCourseSpy).not.toHaveBeenCalled(); // do not load competencies again as already fetched
    });

    it('should load prerequisites and competencies (with associated progress) and display a card for each of them', () => {
        const competency = new Competency();
        const textUnit = new TextUnit();
        competency.id = 1;
        competency.description = 'test';
        competency.lectureUnits = [textUnit];
        competency.userProgress = [{ progress: 70, confidence: 45 } as CompetencyProgress];

        const prerequisitesOfCourseResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: [new Competency()],
            status: 200,
        });
        const competenciesOfCourseResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: [competency, new Competency()],
            status: 200,
        });

        const getAllPrerequisitesForCourseSpy = jest.spyOn(competencyService, 'getAllPrerequisitesForCourse').mockReturnValue(of(prerequisitesOfCourseResponse));
        const getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesOfCourseResponse));

        courseCompetenciesComponent.isCollapsed = false;
        courseCompetenciesComponentFixture.detectChanges();

        const competencyCards = courseCompetenciesComponentFixture.debugElement.queryAll(By.directive(CompetencyCardStubComponent));
        expect(competencyCards).toHaveLength(3); // 1 prerequisite and 2 competencies
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(courseCompetenciesComponent.competencies).toHaveLength(2);
    });
});

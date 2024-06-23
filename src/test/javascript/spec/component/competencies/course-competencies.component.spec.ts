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
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ArtemisTestModule } from '../../test.module';
import { CompetencyCardStubComponent } from './competency-card-stub.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

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
            parent: new MockActivatedRoute({
                params: of({ courseId: '1' }),
            }),
        }),
    }),
});
describe('CourseCompetencies', () => {
    let courseCompetenciesComponentFixture: ComponentFixture<CourseCompetenciesComponent>;
    let courseCompetenciesComponent: CourseCompetenciesComponent;
    let competencyService: CompetencyService;
    let prerequisiteService: PrerequisiteService;
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
                {
                    provide: FeatureToggleService,
                    useValue: {
                        getFeatureToggleActive: () => of(true),
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                courseCompetenciesComponentFixture = TestBed.createComponent(CourseCompetenciesComponent);
                courseCompetenciesComponent = courseCompetenciesComponentFixture.componentInstance;
                competencyService = TestBed.inject(CompetencyService);
                prerequisiteService = TestBed.inject(PrerequisiteService);
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

    it('should load prerequisites and competencies (with associated progress) and display a card for each of them', () => {
        const competency: Competency = {};
        const textUnit = new TextUnit();
        competency.id = 1;
        competency.description = 'test';
        competency.lectureUnits = [textUnit];
        competency.userProgress = [{ progress: 70, confidence: 45 } as CompetencyProgress];

        const competenciesOfCourseResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: [competency, {}],
            status: 200,
        });

        const getAllPrerequisitesForCourseSpy = jest.spyOn(prerequisiteService, 'getAllPrerequisitesForCourse').mockReturnValue(of([{}]));
        jest.spyOn(mockCourseStorageService, 'getCourse').mockReturnValue({ studentCourseAnalyticsDashboardEnabled: true } as any);
        const getAllForCourseSpy = jest.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(competenciesOfCourseResponse));
        const getJoLAllForCourseSpy = jest.spyOn(competencyService, 'getJoLAllForCourse').mockReturnValue(of({} as any));

        courseCompetenciesComponent.isCollapsed = false;
        courseCompetenciesComponentFixture.detectChanges();

        const competencyCards = courseCompetenciesComponentFixture.debugElement.queryAll(By.directive(CompetencyCardStubComponent));
        expect(competencyCards).toHaveLength(3); // 1 prerequisite and 2 competencies
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(getJoLAllForCourseSpy).toHaveBeenCalledOnce();
        expect(courseCompetenciesComponent.competencies).toHaveLength(2);
    });
});

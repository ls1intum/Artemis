import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { Competency, CompetencyProgress, CourseCompetencyType } from 'app/entities/competency.model';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetenciesComponent } from 'app/overview/course-competencies/course-competencies.component';
import { HttpResponse } from '@angular/common/http';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ArtemisTestModule } from '../../test.module';
import { CompetencyCardStubComponent } from './competency-card-stub.component';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

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
    let courseCompetencyService: CourseCompetencyService;
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
                courseCompetencyService = TestBed.inject(CourseCompetencyService);
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
        const competency: Competency = new Competency();
        const textUnit = new TextUnit();
        competency.id = 1;
        competency.description = 'test';
        competency.lectureUnits = [textUnit];
        competency.userProgress = [{ progress: 70, confidence: 45 } as CompetencyProgress];

        const getAllCourseCompetenciesForCourseSpy = jest.spyOn(courseCompetencyService, 'getAllForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [competency, { type: CourseCompetencyType.COMPETENCY }, { type: CourseCompetencyType.PREREQUISITE } as Prerequisite],
                    status: 200,
                }),
            ),
        );
        jest.spyOn(mockCourseStorageService, 'getCourse').mockReturnValue({ studentCourseAnalyticsDashboardEnabled: true } as any);
        const getJoLAllForCourseSpy = jest.spyOn(courseCompetencyService, 'getJoLAllForCourse').mockReturnValue(of({} as any));

        courseCompetenciesComponent.isCollapsed = false;
        courseCompetenciesComponentFixture.detectChanges();

        expect(getAllCourseCompetenciesForCourseSpy).toHaveBeenCalledOnce();
        expect(getJoLAllForCourseSpy).toHaveBeenCalledOnce();
        expect(courseCompetenciesComponent.competencies).toHaveLength(2);
    });
});

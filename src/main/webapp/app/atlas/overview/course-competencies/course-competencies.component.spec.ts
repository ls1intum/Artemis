import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { of } from 'rxjs';
import { Competency, CompetencyLectureUnitLink, CompetencyProgress, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseCompetenciesComponent } from 'app/atlas/overview/course-competencies/course-competencies.component';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CompetencyCardStubComponent } from 'test/helpers/stubs/atlas/competency-card-stub.component';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ScienceService } from 'app/shared/science/science.service';

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
    let courseCompetencyService: CourseCompetencyService;
    const mockCourseStorageService = {
        getCourse: () => {},
        setCourses: () => {},
        subscribeToCourseUpdates: () => of(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CourseCompetenciesComponent, CompetencyCardStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ScienceService),
            ],
        })
            .compileComponents()
            .then(() => {
                courseCompetenciesComponentFixture = TestBed.createComponent(CourseCompetenciesComponent);
                courseCompetenciesComponent = courseCompetenciesComponentFixture.componentInstance;
                courseCompetenciesComponentFixture.componentRef.setInput('courseId', 1);
                courseCompetencyService = TestBed.inject(CourseCompetencyService);
                const accountService = TestBed.inject(AccountService);
                const user = new User();
                user.login = 'testUser';
                accountService.userIdentity.set(user);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        courseCompetenciesComponentFixture.detectChanges();
        expect(courseCompetenciesComponent).toBeDefined();
        // Input is now a signal; route param is used directly for logging only
    });

    it('should load prerequisites and competencies (with associated progress) and display a card for each of them', () => {
        const competency: Competency = new Competency();
        const textUnit = new TextUnit();
        competency.id = 1;
        competency.description = 'test';
        competency.lectureUnitLinks = [new CompetencyLectureUnitLink(competency, textUnit, 1)];
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

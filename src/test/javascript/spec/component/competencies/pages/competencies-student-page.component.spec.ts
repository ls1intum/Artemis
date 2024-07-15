import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { CompetenciesStudentPageComponent } from 'app/course/competencies/pages/competencies-student-page/competencies-student-page.component';
import { AlertService } from 'app/core/util/alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { CompetencyApiService } from 'app/course/competencies/services/competency-api.service';
import { PrerequisiteApiService } from 'app/course/competencies/services/prerequisite-api.service';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { CourseOverviewService } from 'app/overview/course-overview.service';

describe('CompetenciesStudentPageComponent', () => {
    let component: CompetenciesStudentPageComponent;
    let fixture: ComponentFixture<CompetenciesStudentPageComponent>;
    let competencyApiService: CompetencyApiService;
    let prerequisiteApiService: PrerequisiteApiService;
    let alertService: AlertService;
    let courseOverviewService: CourseOverviewService;

    let getCompetenciesSpy: jest.SpyInstance;
    let getPrerequisitesSpy: jest.SpyInstance;

    const courseCompetenciesKey = 'course-competencies';

    const courseId = 1;
    const competencies = <Competency[]>[
        {
            id: 1,
            title: 'Competency 1',
            description: 'Competency 1 description',
            taxonomy: CompetencyTaxonomy.ANALYZE,
        },
        {
            id: 2,
            title: 'Competency 2',
            description: 'Competency 2 description',
            taxonomy: CompetencyTaxonomy.APPLY,
        },
    ];
    const prerequisites = <Prerequisite[]>[
        {
            id: 10,
            title: 'Prerequisite 1',
            description: 'Prerequisite 1 description',
        },
        {
            id: 11,
            title: 'Prerequisite 2',
            description: 'Prerequisite 2 description',
        },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetenciesStudentPageComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: courseId,
                            }),
                        },
                    },
                },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                {
                    provide: CourseOverviewService,
                    useValue: {
                        setSidebarCollapseState: jest.fn(),
                        getSidebarCollapseStateFromStorage: jest.fn(),
                    },
                },
            ],
        }).compileComponents();

        competencyApiService = TestBed.inject(CompetencyApiService);
        prerequisiteApiService = TestBed.inject(PrerequisiteApiService);
        alertService = TestBed.inject(AlertService);
        courseOverviewService = TestBed.inject(CourseOverviewService);

        getCompetenciesSpy = jest.spyOn(competencyApiService, 'getCompetenciesByCourseId').mockResolvedValue(competencies);
        getPrerequisitesSpy = jest.spyOn(prerequisiteApiService, 'getPrerequisitesByCourseId').mockResolvedValue(prerequisites);

        fixture = TestBed.createComponent(CompetenciesStudentPageComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should set isLoading correctly', async () => {
        const loadingSpy = jest.spyOn(component.isLoading, 'set');

        await component.loadData(courseId);

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should load course competencies', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getCompetenciesSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.competencies()).toEqual(competencies);
    });

    it('should load course prerequisites', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getPrerequisitesSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.prerequisites()).toEqual(prerequisites);
    });

    it('should show error when competencies could not be loaded', async () => {
        getCompetenciesSpy.mockRejectedValue(new Error());
        const alertServiceErrorSpy = jest.spyOn(alertService, 'addAlert');

        await component.loadData(courseId);

        expect(getCompetenciesSpy).toHaveBeenCalledOnce();
        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should show error when prerequisites could not be loaded', async () => {
        getPrerequisitesSpy.mockRejectedValue(new Error());
        const alertServiceErrorSpy = jest.spyOn(alertService, 'addAlert');

        await component.loadData(courseId);

        expect(getPrerequisitesSpy).toHaveBeenCalledOnce();
        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should toggle sidebar collapse state', async () => {
        const setSidebarCollapseStateSpy = jest.spyOn(courseOverviewService, 'setSidebarCollapseState');

        component.toggleSidebar();
        expect(component.isCollapsed()).toBeTrue();
        expect(component.isCollapsed()).toBeTrue();
        expect(setSidebarCollapseStateSpy).toHaveBeenCalledWith(courseCompetenciesKey, true);

        component.toggleSidebar();
        expect(component.isCollapsed()).toBeFalse();
        expect(component.isCollapsed()).toBeFalse();
        expect(setSidebarCollapseStateSpy).toHaveBeenCalledWith(courseCompetenciesKey, true);
    });
});

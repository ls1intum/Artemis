import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { CourseCompetenciesStudentPageComponent } from 'app/course/competencies/pages/course-competencies-student-page/course-competencies-student-page.component';
import { AlertService } from 'app/core/util/alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { Competency, CompetencyTaxonomy, CourseCompetency, CourseCompetencyType, getIcon } from 'app/entities/competency.model';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';

describe('CompetenciesStudentPageComponent', () => {
    let component: CourseCompetenciesStudentPageComponent;
    let fixture: ComponentFixture<CourseCompetenciesStudentPageComponent>;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let alertService: AlertService;

    let getCourseCompetenciesSpy: jest.SpyInstance;

    const courseId = 1;
    const competencies = <Competency[]>[
        {
            id: 1,
            title: 'Competency 1',
            description: 'Competency 1 description',
            taxonomy: CompetencyTaxonomy.ANALYZE,
            type: CourseCompetencyType.COMPETENCY,
        },
        {
            id: 2,
            title: 'Competency 2',
            description: 'Competency 2 description',
            taxonomy: CompetencyTaxonomy.APPLY,
            type: CourseCompetencyType.COMPETENCY,
        },
    ];
    const prerequisites = <Prerequisite[]>[
        {
            id: 10,
            title: 'Prerequisite 1',
            description: 'Prerequisite 1 description',
            type: CourseCompetencyType.PREREQUISITE,
        },
        {
            id: 11,
            title: 'Prerequisite 2',
            description: 'Prerequisite 2 description',
            type: CourseCompetencyType.PREREQUISITE,
        },
    ];
    const courseCompetencies = <CourseCompetency[]>[...competencies, ...prerequisites];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetenciesStudentPageComponent],
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

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        alertService = TestBed.inject(AlertService);

        getCourseCompetenciesSpy = jest.spyOn(courseCompetencyApiService, 'getCourseCompetenciesByCourseId').mockResolvedValue(courseCompetencies);

        fixture = TestBed.createComponent(CourseCompetenciesStudentPageComponent);
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

        fixture.detectChanges();
        await fixture.whenStable();

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should load course competencies', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getCourseCompetenciesSpy).toHaveBeenCalledWith(courseId);
    });

    it('should show error when course competencies could not be loaded', async () => {
        getCourseCompetenciesSpy.mockRejectedValue(new Error());
        const alertServiceErrorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getCourseCompetenciesSpy).toHaveBeenCalledOnce();
        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set sideBarData correctly', async () => {
        const competenciesSidebarCards = competencies.map((competency) => getSidebarCardElement(competency));
        const prerequisitesSidebarCards = prerequisites.map((prerequisite) => getSidebarCardElement(prerequisite));
        const sidebarData = <SidebarData>{
            storageId: 'course-competency',
            groupByCategory: true,
            ungroupedData: [...competenciesSidebarCards, ...prerequisitesSidebarCards],
            groupedData: {
                competencies: {
                    entityData: competenciesSidebarCards,
                },
                prerequisites: {
                    entityData: prerequisitesSidebarCards,
                },
            },
        };

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.sidebarData()).toEqual(sidebarData);
    });

    it('should toggle sidebar visibility based on isCollapsed property', () => {
        component.toggleSidebar();
        expect(component.isCollapsed()).toBeTrue();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).not.toBeNull();

        component.toggleSidebar();
        expect(component.isCollapsed()).toBeFalse();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).toBeNull();
    });
});

function getSidebarCardElement(courseCompetency: CourseCompetency) {
    return <SidebarCardElement>{
        id: courseCompetency.id,
        title: courseCompetency.title,
        size: 'M',
        icon: getIcon(courseCompetency.taxonomy),
    };
}

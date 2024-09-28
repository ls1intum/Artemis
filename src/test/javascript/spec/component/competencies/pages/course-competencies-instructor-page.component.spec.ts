import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetenciesInstructorPageComponent } from 'app/course/competencies/pages/competencies-instructor-page/course-competencies-instructor-page.component';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { TranslateService } from '@ngx-translate/core';
import { CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

describe('CourseCompetenciesInstructorPageComponent', () => {
    let component: CourseCompetenciesInstructorPageComponent;
    let fixture: ComponentFixture<CourseCompetenciesInstructorPageComponent>;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let alertService: AlertService;

    const courseId = 1;

    const courseCompetencies: CourseCompetency[] = [
        { id: 1, type: CourseCompetencyType.COMPETENCY },
        { id: 2, type: CourseCompetencyType.PREREQUISITE },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetenciesInstructorPageComponent],
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                {
                    provide: LocalStorageService,
                    useClass: MockLocalStorageService,
                },
                {
                    provide: CourseCompetencyApiService,
                    useValue: {
                        getCourseCompetenciesByCourseId: jest.fn(),
                    },
                },
                {
                    provide: FeatureToggleService,
                    useValue: {
                        getFeatureToggleActive: jest.fn(() => of(true)),
                    },
                },
            ],
        }).compileComponents();

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        alertService = TestBed.inject(AlertService);

        jest.spyOn(courseCompetencyApiService, 'getCourseCompetenciesByCourseId').mockReturnValue(Promise.resolve(courseCompetencies));

        fixture = TestBed.createComponent(CourseCompetenciesInstructorPageComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseId()).toBe(courseId);
    });

    it('should load course competencies', async () => {
        const getCourseCompetenciesSpy = jest.spyOn(courseCompetencyApiService, 'getCourseCompetenciesByCourseId');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getCourseCompetenciesSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.courseCompetencies()).toEqual(courseCompetencies);
        expect(component.competencies()).toEqual([courseCompetencies[0]]);
        expect(component.prerequisites()).toEqual([courseCompetencies[1]]);
    });

    it('should show alert on error', async () => {
        jest.spyOn(courseCompetencyApiService, 'getCourseCompetenciesByCourseId').mockRejectedValue(new Error('Error'));
        const errorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should open course competencies relation modal', async () => {
        const openModalSpy = jest.spyOn(fixture.debugElement.injector.get(NgbModal), 'open').mockReturnValue({
            componentInstance: {
                courseId: courseId,
                courseCompetencies: component.courseCompetencies,
            },
        } as any);

        fixture.detectChanges();
        await fixture.whenStable();

        const openModalButton = fixture.nativeElement.querySelector('#openCourseCompetencyRelationsButton');

        expect(openModalButton).toBeTruthy();
        openModalButton.click();

        expect(openModalSpy).toHaveBeenCalledOnce();
    });

    it('should remove course competency on deletion', async () => {
        const courseCompetencyId = courseCompetencies[0].id;

        fixture.detectChanges();
        await fixture.whenStable();

        component['onCourseCompetencyDeletion'](courseCompetencyId!);

        expect(component.courseCompetencies()).toEqual([courseCompetencies[1]]);
    });

    it('should add imported course competencies', async () => {
        const importedCourseCompetencies: CourseCompetency[] = [
            { id: 3, type: CourseCompetencyType.COMPETENCY },
            { id: 4, type: CourseCompetencyType.COMPETENCY },
        ];

        fixture.detectChanges();
        await fixture.whenStable();

        component['onCourseCompetenciesImport'](importedCourseCompetencies);

        expect(component.courseCompetencies()).toEqual([...courseCompetencies, ...importedCourseCompetencies]);
        expect(component.competencies()).toEqual([courseCompetencies[0], ...importedCourseCompetencies]);
        expect(component.prerequisites()).toEqual([courseCompetencies[1]]);
    });
});

import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImportCourseCompetenciesComponent } from 'app/atlas/manage/import/import-course-competencies.component';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { CourseCompetency, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { PageableSearch } from 'app/shared/table/pageable-table';
import { Component } from '@angular/core';
import { SortService } from 'app/shared/service/sort.service';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

@Component({ template: '' })
class DummyImportComponent extends ImportCourseCompetenciesComponent {
    entityType = 'dummy';

    onSubmit(): void {}
}

describe('ImportCourseCompetenciesComponent', () => {
    setupTestBed({ zoneless: true });
    let componentFixture: ComponentFixture<DummyImportComponent>;
    let component: DummyImportComponent;
    let courseCompetencyService: CourseCompetencyService;
    let getCourseCompetenciesSpy: ReturnType<typeof vi.spyOn>;
    let getForImportSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId: 1 }) },
                    } as ActivatedRoute,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(DummyImportComponent);
                component = componentFixture.componentInstance;

                courseCompetencyService = TestBed.inject(CourseCompetencyService);

                getForImportSpy = vi.spyOn(courseCompetencyService, 'getForImport');
                getCourseCompetenciesSpy = vi.spyOn(courseCompetencyService, 'getAllForCourse');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should initialize values correctly', async () => {
        getCourseCompetenciesSpy.mockReturnValue(
            of(
                new HttpResponse({
                    body: <CourseCompetency[]>[
                        { id: 1, type: CourseCompetencyType.COMPETENCY },
                        { id: 2, type: CourseCompetencyType.COMPETENCY },
                        { id: 3, type: CourseCompetencyType.PREREQUISITE, linkedCourseCompetency: { id: 11 } },
                        { id: 4, type: CourseCompetencyType.PREREQUISITE, linkedCourseCompetency: { id: 12 } },
                    ],
                    status: 200,
                }),
            ),
        );
        getForImportSpy.mockReturnValue(
            of({
                resultsOnPage: [{ id: 1 }, { id: 2 }, { id: 3 }],
                numberOfPages: 1,
            }),
        );

        componentFixture.detectChanges();

        expect(component.disabledIds).toHaveLength(6);
        expect(component.disabledIds).toEqual(expect.arrayContaining([1, 2, 3, 4, 11, 12]));
        expect(component.searchedCourseCompetencies.resultsOnPage).toHaveLength(3);
    });

    it('should cancel', () => {
        componentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.onCancel();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should deactivate correctly', () => {
        component.isLoading = false;
        expect(component.canDeactivate()).toBeTruthy();

        component.isLoading = true;
        expect(component.canDeactivate()).toBeFalsy();

        component.isSubmitted = true;
        expect(component.canDeactivate()).toBeTruthy();
    });

    it('should perform search on search change', () => {
        getForImportSpy.mockReturnValue(
            of({
                resultsOnPage: [{ id: 1 }],
                numberOfPages: 1,
            }),
        );
        componentFixture.detectChanges();

        component.searchChange({} as PageableSearch);

        expect(getForImportSpy).toHaveBeenCalled();
    });

    it('should perform search on filter change', () => {
        getForImportSpy.mockReturnValue(
            of({
                resultsOnPage: [{ id: 1 }],
                numberOfPages: 1,
            }),
        );
        componentFixture.detectChanges();

        component.filterChange({ title: '', description: '', semester: '', courseTitle: '' });

        expect(getForImportSpy).toHaveBeenCalled();
    });

    it('should sort selected', () => {
        const sortService = TestBed.inject(SortService);
        const sortSpy = vi.spyOn(sortService, 'sortByProperty');

        component.sortSelected({} as PageableSearch);

        expect(sortSpy).toHaveBeenCalled();
    });

    it('should add competencies to selected', () => {
        expect(component.selectedCourseCompetencies.resultsOnPage).toHaveLength(0);

        component.selectCompetency({ id: 1 });
        expect(component.selectedCourseCompetencies.resultsOnPage).toHaveLength(1);
        expect(component.disabledIds).toHaveLength(1);

        //no id so does not get added to disabled ids
        component.selectCompetency({});
        expect(component.selectedCourseCompetencies.resultsOnPage).toHaveLength(2);
        expect(component.disabledIds).toHaveLength(1);
    });

    it('should remove competencies from selected', () => {
        component.selectedCourseCompetencies.resultsOnPage = [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }];
        component.disabledIds = [1, 2, 3, 4];

        component.removeCompetency({ id: 1 });
        expect(component.selectedCourseCompetencies.resultsOnPage).toHaveLength(3);
        expect(component.disabledIds).toHaveLength(3);

        //is not part of the competencies so nothing happens.
        component.removeCompetency({ id: 5 });
        //has no id so nothing happens
        component.removeCompetency({});
        expect(component.selectedCourseCompetencies.resultsOnPage).toHaveLength(3);
        expect(component.disabledIds).toHaveLength(3);
    });

    it('should not deactivate with pending changes', () => {
        let canDeactivate;
        component['isSubmitted'] = true;
        component.selectedCourseCompetencies = { resultsOnPage: [{ id: 1 }], numberOfPages: 0 };
        canDeactivate = component.canDeactivate();
        expect(canDeactivate).toBeTruthy();

        component['isSubmitted'] = false;
        canDeactivate = component.canDeactivate();
        expect(canDeactivate).toBeFalsy();
    });
});

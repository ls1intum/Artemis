import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { ImportCourseCompetenciesComponent } from 'app/course/competencies/import-competencies/import-course-competencies.component';
import { FormsModule } from 'app/forms/forms.module';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { Competency } from 'app/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { PageableSearch } from 'app/shared/table/pageable-table';
import { Component } from '@angular/core';
import { SortService } from 'app/shared/service/sort.service';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { Prerequisite } from 'app/entities/prerequisite.model';

@Component({ template: '' })
class DummyImportComponent extends ImportCourseCompetenciesComponent {
    entityType = 'dummy';

    onSubmit(): void {}
}

describe('ImportCourseCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<DummyImportComponent>;
    let component: DummyImportComponent;
    let competencyService: CompetencyService;
    let prerequisiteService: PrerequisiteService;
    let getAllSpy: any;
    let getForImportSpy: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [ImportCourseCompetenciesComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId: 1 }) },
                    } as ActivatedRoute,
                },
                { provide: Router, useClass: MockRouter },
                MockProvider(CompetencyService),
                MockProvider(PrerequisiteService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(DummyImportComponent);
                component = componentFixture.componentInstance;
                competencyService = TestBed.inject(CompetencyService);
                prerequisiteService = TestBed.inject(PrerequisiteService);
                getAllSpy = jest.spyOn(competencyService, 'getAllForCourse');
                getForImportSpy = jest.spyOn(competencyService, 'getForImport');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should initialize values correctly', () => {
        jest.spyOn(prerequisiteService, 'getAllPrerequisitesForCourse').mockReturnValue(
            of([
                { id: 3, linkedCourseCompetency: { id: 11 } },
                { id: 4, linkedCourseCompetency: { id: 12 } },
            ] as Prerequisite[]),
        );
        getAllSpy.mockReturnValue(
            of({
                body: [{ id: 1 }, { id: 2 }],
            } as HttpResponse<Competency[]>),
        );
        getForImportSpy.mockReturnValue(
            of({
                resultsOnPage: [{ id: 1 }, { id: 2 }, { id: 3 }],
                numberOfPages: 1,
            }),
        );

        componentFixture.detectChanges();

        expect(component.disabledIds).toHaveLength(4);
        expect(component.disabledIds).toContainAllValues([1, 2, 11, 12]);
        expect(component.searchedCourseCompetencies.resultsOnPage).toHaveLength(3);
    });

    it('should cancel', () => {
        componentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.onCancel();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should deactivate correctly', () => {
        component.isLoading = false;
        expect(component.canDeactivate()).toBeTrue();

        component.isLoading = true;
        expect(component.canDeactivate()).toBeFalse();

        component.isSubmitted = true;
        expect(component.canDeactivate()).toBeTrue();
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
        const sortSpy = jest.spyOn(sortService, 'sortByProperty');

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

    it('should not unload with pending changes', () => {
        const deactivateWarningSpy = jest.spyOn(component, 'canDeactivateWarning', 'get');

        component['isSubmitted'] = true;
        component.selectedCourseCompetencies = { resultsOnPage: [{ id: 1 }], numberOfPages: 0 };
        component['unloadNotification']({ returnValue: '' });
        expect(deactivateWarningSpy).not.toHaveBeenCalled();

        component['isSubmitted'] = false;
        component['unloadNotification']({ returnValue: '' });
        expect(deactivateWarningSpy).toHaveBeenCalled();
    });
});

import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ImportCompetenciesComponent } from 'app/course/competencies/import-competencies/import-competencies.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule } from 'app/forms/forms.module';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { Competency, CompetencyWithTailRelationDTO } from 'app/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { ImportCompetenciesTableStubComponent } from './import-competencies-table-stub.component';
import { CompetencySearchStubComponent } from './competency-search-stub.component';
import { By } from '@angular/platform-browser';
import { CompetencyFilter, PageableSearch } from 'app/shared/table/pageable-table';

describe('ImportCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<ImportCompetenciesComponent>;
    let component: ImportCompetenciesComponent;
    let competencyService: CompetencyService;
    let getAllSpy: any;
    let getForImportSpy: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [ImportCompetenciesComponent, MockPipe(ArtemisTranslatePipe), ButtonComponent, ImportCompetenciesTableStubComponent, CompetencySearchStubComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 1 }),
                },
                { provide: Router, useClass: MockRouter },
                MockProvider(CompetencyService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ImportCompetenciesComponent);
                component = componentFixture.componentInstance;
                competencyService = TestBed.inject(CompetencyService);
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

        expect(component.disabledIds).toHaveLength(2);
        expect(component.searchedCompetencies.resultsOnPage).toHaveLength(3);
    });

    it('should submit', () => {
        const competencyDTOs: CompetencyWithTailRelationDTO[] = [{ competency: { id: 1 } }, { competency: { id: 2 } }];
        const importBulkSpy = jest.spyOn(competencyService, 'importBulk').mockReturnValue(
            of({
                body: competencyDTOs,
            } as HttpResponse<CompetencyWithTailRelationDTO[]>),
        );
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.onSubmit();

        expect(importBulkSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        componentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const cancelButton = componentFixture.debugElement.nativeElement.querySelector('#cancelButton > .jhi-btn');

        cancelButton.click();

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

    it('should handle competency search callback', () => {
        getForImportSpy.mockReturnValue(
            of({
                resultsOnPage: [{ id: 1 }],
                numberOfPages: 1,
            }),
        );
        const filterChangeSpy = jest.spyOn(component, 'filterChange');
        componentFixture.detectChanges();

        const competencySearchComponent: CompetencySearchStubComponent = componentFixture.debugElement.query(By.directive(CompetencySearchStubComponent)).componentInstance;
        competencySearchComponent.searchChange.emit({} as CompetencyFilter);

        expect(filterChangeSpy).toHaveBeenCalledOnce();
    });

    it('should handle competency table callbacks', () => {
        const searchChangeSpy = jest.spyOn(component, 'searchChange');
        const sortSelectedSpy = jest.spyOn(component, 'sortSelected');
        getForImportSpy.mockReturnValue(
            of({
                resultsOnPage: [{ id: 1 }],
                numberOfPages: 1,
            }),
        );
        //set results on page so competency tables show up
        component.searchedCompetencies.resultsOnPage = [{ id: 1 }];
        component.selectedCompetencies.resultsOnPage = [{ id: 1 }];

        componentFixture.detectChanges();

        const competencyTables = componentFixture.debugElement.queryAll(By.directive(ImportCompetenciesTableStubComponent));
        expect(competencyTables).toHaveLength(2);
        for (const element of competencyTables) {
            const table: ImportCompetenciesTableStubComponent = element.componentInstance;
            table.searchChange.emit({} as PageableSearch);
        }

        expect(searchChangeSpy).toHaveBeenCalledOnce();
        expect(sortSelectedSpy).toHaveBeenCalledOnce();
    });

    it('should add competencies to selected', () => {
        expect(component.selectedCompetencies.resultsOnPage).toHaveLength(0);

        component.selectCompetency({ id: 1 });
        expect(component.selectedCompetencies.resultsOnPage).toHaveLength(1);
        expect(component.disabledIds).toHaveLength(1);

        //no id so does not get added to disabled ids
        component.selectCompetency({});
        expect(component.selectedCompetencies.resultsOnPage).toHaveLength(2);
        expect(component.disabledIds).toHaveLength(1);
    });

    it('should remove competencies from selected', () => {
        component.selectedCompetencies.resultsOnPage = [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }];
        component.disabledIds = [1, 2, 3, 4];

        component.removeCompetency({ id: 1 });
        expect(component.selectedCompetencies.resultsOnPage).toHaveLength(3);
        expect(component.disabledIds).toHaveLength(3);

        //is not part of the competencies so nothing happens.
        component.removeCompetency({ id: 5 });
        //has no id so nothing happens
        component.removeCompetency({});
        expect(component.selectedCompetencies.resultsOnPage).toHaveLength(3);
        expect(component.disabledIds).toHaveLength(3);
    });
});

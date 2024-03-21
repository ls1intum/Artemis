import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ImportCompetenciesTableComponent } from 'app/course/competencies/import-competencies/import-competencies-table.component';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';

describe('ImportCompetenciesTableComponent', () => {
    let componentFixture: ComponentFixture<ImportCompetenciesTableComponent>;
    let component: ImportCompetenciesTableComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule],
            declarations: [
                ImportCompetenciesTableComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(TranslateDirective),
                MockComponent(NgbPagination),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ImportCompetenciesTableComponent);
                component = componentFixture.componentInstance;
                component.content = { resultsOnPage: [], numberOfPages: 1 };
                component.search = {
                    sortedColumn: 'ID',
                    sortingOrder: SortingOrder.DESCENDING,
                    pageSize: 10,
                    page: 1,
                };
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should change page', () => {
        const searchEmitSpy = jest.spyOn(component.searchChange, 'emit');

        component.onPageChange(5);
        expect(component.search.page).toBe(5);
        expect(searchEmitSpy).toHaveBeenCalledOnce();
    });

    it('should change sort', () => {
        const searchEmitSpy = jest.spyOn(component.searchChange, 'emit');

        expect(component.search.sortingOrder).toBe(SortingOrder.DESCENDING);
        expect(component.search.sortedColumn).toBe('ID');

        component.onSortChange({ predicate: 'TITLE', ascending: true });

        expect(component.search.sortingOrder).toBe(SortingOrder.ASCENDING);
        expect(component.search.sortedColumn).toBe('TITLE');
        expect(searchEmitSpy).toHaveBeenCalledOnce();
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ImportCompetenciesTableComponent } from 'app/atlas/manage/import/import-competencies-table.component';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';

describe('ImportCompetenciesTableComponent', () => {
    let componentFixture: ComponentFixture<ImportCompetenciesTableComponent>;
    let component: ImportCompetenciesTableComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule],
            declarations: [ImportCompetenciesTableComponent, MockDirective(SortDirective), MockDirective(TranslateDirective), MockComponent(NgbPagination)],
            providers: [],
        }).compileComponents();

        componentFixture = TestBed.createComponent(ImportCompetenciesTableComponent);
        component = componentFixture.componentInstance;
        componentFixture.componentRef.setInput('content', { resultsOnPage: [], numberOfPages: 1 });
        componentFixture.componentRef.setInput('search', {
            sortedColumn: 'ID',
            sortingOrder: SortingOrder.DESCENDING,
            pageSize: 10,
            page: 1,
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
        component.onPageChange(5);
        expect(component.search().page).toBe(5);
    });

    it('should change sort', () => {
        expect(component.search().sortingOrder).toBe(SortingOrder.DESCENDING);
        expect(component.search().sortedColumn).toBe('ID');

        component.onSortChange({ predicate: 'TITLE', ascending: true });

        expect(component.search().sortingOrder).toBe(SortingOrder.ASCENDING);
        expect(component.search().sortedColumn).toBe('TITLE');
        expect(component.ascending).toBeTrue();
    });
});

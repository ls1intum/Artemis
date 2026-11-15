import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { TumAetUiPaginatorComponent } from '@tumaet/ui-angular';
import { MockDirective } from 'ng-mocks';
import { ImportCompetenciesTableComponent } from 'app/atlas/manage/import/import-competencies-table.component';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ImportCompetenciesTableComponent', () => {
    let componentFixture: ComponentFixture<ImportCompetenciesTableComponent>;
    let component: ImportCompetenciesTableComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, ImportCompetenciesTableComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
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
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should change page', () => {
        component.onPageChange(5);
        expect(component.search().page).toBe(5);
    });

    it('converts the zero-based paginator page to the one-based search page', () => {
        componentFixture.detectChanges();

        componentFixture.debugElement.query(By.directive(TumAetUiPaginatorComponent)).triggerEventHandler('pageChange', 4);

        expect(component.search().page).toBe(5);
    });

    it('should change sort', () => {
        expect(component.search().sortingOrder).toBe(SortingOrder.DESCENDING);
        expect(component.search().sortedColumn).toBe('ID');

        component.onSortChange({ field: 'TITLE', order: 1 });

        expect(component.search().sortingOrder).toBe(SortingOrder.ASCENDING);
        expect(component.search().sortedColumn).toBe('TITLE');
    });
});

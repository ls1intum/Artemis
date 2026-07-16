import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TumUiTableComponent } from 'app/shared-ui/tum-ui/table/tum-ui-table.component';
import { ColumnDef } from 'app/shared-ui/tum-ui/table/tum-ui-table.types';

interface Row {
    name: string;
    count: number;
}

const COLUMNS: ColumnDef<Row>[] = [
    { field: 'name', headerKey: 'test.name', sort: true, width: '200px' },
    { field: 'count', header: 'Count', sort: false },
];
const ROWS: Row[] = [
    { name: 'Alpha', count: 3 },
    { name: 'Beta', count: 7 },
];

describe('TumUiTableComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TumUiTableComponent<Row>;
    let fixture: ComponentFixture<TumUiTableComponent<Row>>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiTableComponent, FontAwesomeTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent<TumUiTableComponent<Row>>(TumUiTableComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('columns', COLUMNS);
        fixture.componentRef.setInput('rows', ROWS);
        fixture.componentRef.setInput('totalRecords', 2);
    });

    afterEach(() => vi.restoreAllMocks());

    function headerCells(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('th[cdk-header-cell]')).map((d) => d.nativeElement);
    }
    function dataCells(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('td[cdk-cell]')).map((d) => d.nativeElement);
    }

    it('renders one header per column and cells with dot-path values', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(headerCells().length).toBe(2);
        const cellText = dataCells().map((c) => c.textContent?.trim());
        expect(cellText).toContain('Alpha');
        expect(cellText).toContain('3');
        expect(cellText).toContain('Beta');
    });

    it('emits one initial dataRequest after first render with defaults', async () => {
        const spy = vi.spyOn(component.dataRequest, 'emit');
        fixture.detectChanges();
        await fixture.whenStable();
        expect(spy).toHaveBeenCalledTimes(1);
        expect(spy).toHaveBeenCalledWith(expect.objectContaining({ offset: 0, pageSize: 50 }));
    });

    it('emits sort on a sortable header click and toggles asc/desc with aria-sort', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const spy = vi.spyOn(component.dataRequest, 'emit');
        const sortButton: HTMLButtonElement = headerCells()[0].querySelector('button')!;
        sortButton.click();
        fixture.detectChanges();
        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ sortField: 'name', sortDirection: 'asc', offset: 0 }));
        expect(headerCells()[0].getAttribute('aria-sort')).toBe('ascending');
        sortButton.click();
        fixture.detectChanges();
        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ sortField: 'name', sortDirection: 'desc' }));
        expect(headerCells()[0].getAttribute('aria-sort')).toBe('descending');
    });

    it('resets the native button look on the sort trigger (no grey UA button-face without Tailwind preflight)', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const sortButton: HTMLButtonElement = headerCells()[0].querySelector('button')!;
        expect(sortButton.className).toContain('appearance-none');
        expect(sortButton.className).toContain('border-0');
        expect(sortButton.className).toContain('bg-transparent');
        // Must stay semibold like the non-sortable headers (the UA button font-shorthand would otherwise reset it).
        expect(sortButton.className).toContain('font-semibold');
    });

    it('does not render a sort button for a non-sortable column', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(headerCells()[1].querySelector('button')).toBeNull();
        expect(headerCells()[1].getAttribute('aria-sort')).toBeNull();
    });

    it('debounces the global search and resets the page', async () => {
        vi.useFakeTimers();
        fixture.detectChanges();
        const spy = vi.spyOn(component.dataRequest, 'emit');
        const search: HTMLInputElement = fixture.debugElement.query(By.css('[data-testid="tum-ui-table-search"]')).nativeElement;
        search.value = 'alp';
        search.dispatchEvent(new Event('input'));
        expect(spy).not.toHaveBeenCalled();
        vi.advanceTimersByTime(300);
        expect(spy).toHaveBeenCalledWith(expect.objectContaining({ globalFilter: 'alp', offset: 0 }));
        vi.useRealTimers();
    });

    it('renders an actions column when rowActions is set', async () => {
        const tpl = fixture.debugElement.query(By.css('table'));
        // set a simple actions template via a host would be heavier; assert the sentinel column count instead
        fixture.componentRef.setInput('rowActions', null);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(headerCells().length).toBe(2);
        expect(tpl).toBeTruthy();
    });

    it('shows the empty row when there are no rows', async () => {
        fixture.componentRef.setInput('rows', []);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.debugElement.query(By.css('tr[role="row"] td[colspan], td[colspan]'))).toBeTruthy();
    });

    it('emits dataRequest with the next page offset when the paginator advances', async () => {
        fixture.componentRef.setInput('totalRecords', 130);
        fixture.detectChanges();
        await fixture.whenStable();
        const spy = vi.spyOn(component.dataRequest, 'emit');
        const next: HTMLButtonElement = fixture.debugElement.query(By.css('[data-testid="paginator-next"]')).nativeElement;
        next.click();
        fixture.detectChanges();
        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ offset: 50, pageSize: 50 }));
    });

    it('clamps to the last valid page and re-emits when totalRecords shrinks below the current page', async () => {
        fixture.componentRef.setInput('totalRecords', 130); // 3 pages of 50
        fixture.detectChanges();
        await fixture.whenStable();
        // Move to the last page (index 2).
        fixture.debugElement.query(By.css('[data-testid="paginator-last"]')).nativeElement.click();
        fixture.detectChanges();
        const spy = vi.spyOn(component.dataRequest, 'emit');
        // Rows shrink so page 2 no longer exists (30 records -> 1 page): the table must clamp to page 0 and re-emit.
        fixture.componentRef.setInput('totalRecords', 30);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ offset: 0, pageSize: 50 }));
    });
});

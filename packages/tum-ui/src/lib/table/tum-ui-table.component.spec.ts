import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TumUiTableComponent } from './tum-ui-table.component';
import { ColumnDef } from './tum-ui-table.types';

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

@Component({
    imports: [TumUiTableComponent],
    template: `
        <tum-ui-table [columns]="columns" [rows]="rows" [totalRecords]="2" [rowActions]="actions" />
        <ng-template #actions let-row>
            <button type="button">Edit {{ row.name }}</button>
        </ng-template>
    `,
})
class ActionsHostComponent {
    columns = COLUMNS;
    rows = ROWS;
}

describe('TumUiTableComponent', () => {
    let component: TumUiTableComponent<Row>;
    let fixture: ComponentFixture<TumUiTableComponent<Row>>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiTableComponent, ActionsHostComponent, FontAwesomeTestingModule],
        }).compileComponents();
        fixture = TestBed.createComponent<TumUiTableComponent<Row>>(TumUiTableComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('columns', COLUMNS);
        fixture.componentRef.setInput('rows', ROWS);
        fixture.componentRef.setInput('totalRecords', 2);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

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

    it('announces the table as busy while loading', () => {
        fixture.componentRef.setInput('loading', true);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('table')).nativeElement.getAttribute('aria-busy')).toBe('true');
    });

    it('emits one initial dataRequest after first render with defaults', async () => {
        const spy = vi.spyOn(component.dataRequest, 'emit');
        fixture.detectChanges();
        await fixture.whenStable();
        expect(spy).toHaveBeenCalledTimes(1);
        expect(spy).toHaveBeenCalledWith(expect.objectContaining({ pageIndex: 0, pageSize: 50 }));
    });

    it('emits sort on a sortable header click and toggles asc/desc with aria-sort', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const spy = vi.spyOn(component.dataRequest, 'emit');
        const sortButton: HTMLButtonElement = headerCells()[0].querySelector('button')!;
        sortButton.click();
        fixture.detectChanges();
        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ sort: { field: 'name', direction: 'asc' }, pageIndex: 0 }));
        expect(headerCells()[0].getAttribute('aria-sort')).toBe('ascending');
        sortButton.click();
        fixture.detectChanges();
        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ sort: { field: 'name', direction: 'desc' } }));
        expect(headerCells()[0].getAttribute('aria-sort')).toBe('descending');
    });

    it('exposes aria-sort="none" on a sortable-but-unsorted column (announced as sortable) and none on a non-sortable one', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(headerCells()[0].getAttribute('aria-sort')).toBe('none');
        expect(headerCells()[1].getAttribute('aria-sort')).toBeNull();
    });

    it('debounces the global search and resets the page', async () => {
        vi.useFakeTimers();
        fixture.detectChanges();
        const spy = vi.spyOn(component.dataRequest, 'emit');
        const search: HTMLInputElement = fixture.debugElement.query(By.css('input[type="search"]')).nativeElement;
        search.value = 'alp';
        search.dispatchEvent(new Event('input'));
        expect(spy).not.toHaveBeenCalled();
        vi.advanceTimersByTime(300);
        expect(spy).toHaveBeenCalledWith(expect.objectContaining({ searchTerm: 'alp', pageIndex: 0 }));
        vi.useRealTimers();
    });

    it('renders an actions column with the provided template when rowActions is set', async () => {
        const host = TestBed.createComponent(ActionsHostComponent);
        host.detectChanges();
        await host.whenStable();
        host.detectChanges();
        const headers = host.debugElement.queryAll(By.css('th[cdk-header-cell]'));
        expect(headers.length).toBe(3);
        expect(headers.at(-1)?.nativeElement.textContent.trim()).toBe('Actions');
        const actions = host.debugElement.queryAll(By.css('td[cdk-cell] button'));
        expect(actions.length).toBe(2);
        expect(actions[0].nativeElement.textContent).toContain('Edit Alpha');
    });

    it('renders no actions column when rowActions is unset', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(headerCells().length).toBe(2);
    });

    it('shows the empty row when there are no rows', async () => {
        fixture.componentRef.setInput('rows', []);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.debugElement.query(By.css('tr[role="row"] td[colspan], td[colspan]'))).toBeTruthy();
    });

    it('emits dataRequest with the next page index when the paginator advances', async () => {
        fixture.componentRef.setInput('totalRecords', 130);
        fixture.detectChanges();
        await fixture.whenStable();
        const spy = vi.spyOn(component.dataRequest, 'emit');
        const next: HTMLButtonElement = fixture.debugElement.query(By.css('button[aria-label="Next page"]')).nativeElement;
        next.click();
        fixture.detectChanges();
        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ pageIndex: 1, pageSize: 50 }));
    });

    it('clamps to the last valid page and re-emits when totalRecords shrinks below the current page', async () => {
        fixture.componentRef.setInput('totalRecords', 130);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.debugElement.query(By.css('button[aria-label="Last page"]')).nativeElement.click();
        fixture.detectChanges();
        const spy = vi.spyOn(component.dataRequest, 'emit');
        fixture.componentRef.setInput('totalRecords', 30);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ pageIndex: 0, pageSize: 50 }));
    });

    it('returns to the first page and re-emits when the consumer calls resetPage', async () => {
        fixture.componentRef.setInput('totalRecords', 130);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.debugElement.query(By.css('button[aria-label="Last page"]')).nativeElement.click();
        fixture.detectChanges();
        const spy = vi.spyOn(component.dataRequest, 'emit');

        component.resetPage();

        expect(spy).toHaveBeenLastCalledWith(expect.objectContaining({ pageIndex: 0, pageSize: 50 }));
    });

    it('renders a help control named by the header tooltip only for columns that declare one', async () => {
        fixture.componentRef.setInput('columns', [{ ...COLUMNS[0], headerTooltip: 'How this column is measured' }, COLUMNS[1]]);
        fixture.detectChanges();
        await fixture.whenStable();
        const helpControls = fixture.debugElement.queryAll(By.css('th button[aria-label]'));
        expect(helpControls.length).toBe(1);
        expect(helpControls[0].nativeElement.getAttribute('aria-label')).toBe('How this column is measured');
    });

    it('does not re-emit when resetPage is called while already on the first page', async () => {
        fixture.componentRef.setInput('totalRecords', 130);
        fixture.detectChanges();
        await fixture.whenStable();
        const spy = vi.spyOn(component.dataRequest, 'emit');

        component.resetPage();

        expect(spy).not.toHaveBeenCalled();
    });
});

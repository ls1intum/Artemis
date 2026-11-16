import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiPaginatorComponent } from '@tumaet/ui-angular';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TableViewComponent, TableViewLazyLoadEvent } from './table-view';

interface StudentRow {
    id: number;
    name: string;
}

describe('TableView rendered behavior', () => {
    let fixture: ComponentFixture<TableViewComponent<StudentRow>>;
    const students = [
        { id: 1, name: 'Ada Lovelace' },
        { id: 2, name: 'Grace Hopper' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TableViewComponent], providers: [{ provide: TranslateService, useClass: MockTranslateService }] }).compileComponents();
        fixture = TestBed.createComponent(TableViewComponent<StudentRow>);
        fixture.componentRef.setInput('cols', [{ field: 'name', header: 'Student', sort: true }]);
        fixture.componentRef.setInput('vals', students);
        fixture.componentRef.setInput('options', { lazy: false, selectionMode: 'multiple', showSearch: false });
        fixture.detectChanges();
    });

    it('renders native TUM UI table rows and protects disabled student selections', () => {
        fixture.componentRef.setInput('isRowDisabled', (row: StudentRow) => row.id === 2);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('table[tumAetUiTable]')).toBeTruthy();
        const checkboxes: NodeListOf<HTMLInputElement> = fixture.nativeElement.querySelectorAll('tbody input[type="checkbox"]');
        expect(checkboxes).toHaveLength(2);
        expect(checkboxes[1].disabled).toBe(true);
        checkboxes[0].click();
        expect(fixture.componentInstance.tableSelection).toEqual([students[0]]);
    });

    it('sorts client rows without changing the input array', () => {
        const sortButton: HTMLButtonElement = fixture.nativeElement.querySelector('th button');
        sortButton.click();
        sortButton.click();
        fixture.detectChanges();
        const cells: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('tbody tr td:last-child');
        const names = Array.from(cells).map((cell) => cell.textContent?.trim());
        expect(names).toEqual(['Grace Hopper', 'Ada Lovelace']);
        expect(students[0].name).toBe('Ada Lovelace');
    });
});

describe('TableView frame wiring', () => {
    let fixture: ComponentFixture<TableViewComponent<StudentRow>>;
    const manyStudents: StudentRow[] = Array.from({ length: 25 }, (_, index) => ({ id: index + 1, name: `Student ${String(index + 1).padStart(2, '0')}` }));

    function create(options: Record<string, unknown>, values: StudentRow[] = manyStudents) {
        fixture = TestBed.createComponent(TableViewComponent<StudentRow>);
        fixture.componentRef.setInput('cols', [{ field: 'name', header: 'Student', sort: true, filter: true, filterType: 'text' }]);
        fixture.componentRef.setInput('vals', values);
        fixture.componentRef.setInput('options', { lazy: false, ...options });
        fixture.detectChanges();
    }

    const element = () => fixture.nativeElement as HTMLElement;
    const toolbar = () => element().querySelector<HTMLElement>('[tumAetUiTableToolbar]')!;
    const footer = () => element().querySelector<HTMLElement>('[tumAetUiTableFooter]')!;
    const paginator = () => fixture.debugElement.query(By.directive(TumAetUiPaginatorComponent)).componentInstance as TumAetUiPaginatorComponent;
    const renderedNames = () => Array.from(element().querySelectorAll('tbody tr td')).map((cell) => cell.textContent?.trim());

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TableViewComponent], providers: [{ provide: TranslateService, useClass: MockTranslateService }] }).compileComponents();
    });

    it('should mark the host as filling its parent only when the fill option is set', () => {
        create({ fill: true, showSearch: false });
        expect(element().classList.contains('jhi-table-view-fill')).toBe(true);
        fixture.destroy();

        create({ showSearch: false });
        expect(element().classList.contains('jhi-table-view-fill')).toBe(false);
    });

    it('should hide the toolbar without a search or table actions and show it with a search', () => {
        create({ showSearch: false });
        expect(toolbar().hidden).toBe(true);
        expect(toolbar().querySelector('jhi-search-filter')).toBeNull();
        fixture.destroy();

        create({ showSearch: true });
        expect(toolbar().hidden).toBe(false);
        expect(toolbar().querySelector('jhi-search-filter')).not.toBeNull();
    });

    it('should hide the footer paginator when the table is not paginated and then render all rows', () => {
        create({ paginated: false, showSearch: false });

        expect(footer().hidden).toBe(true);
        expect(element().querySelectorAll('tbody tr')).toHaveLength(25);
    });

    it('should show a paginator with the page size and total of the rows', () => {
        create({ paginated: true, showSearch: false, pageSize: 10 });

        expect(footer().hidden).toBe(false);
        expect(paginator().totalRecords()).toBe(25);
        expect(paginator().pageSize()).toBe(10);
        expect(paginator().page()).toBe(0);
        expect(element().querySelectorAll('tbody tr')).toHaveLength(10);
    });

    it('should convert the zero-based page of the paginator into the first row of the page', () => {
        create({ paginated: true, showSearch: false, pageSize: 10 });

        paginator().pageChange.emit(2);
        fixture.detectChanges();

        expect(renderedNames()).toEqual(['Student 21', 'Student 22', 'Student 23', 'Student 24', 'Student 25']);
        expect(paginator().page()).toBe(2);
    });

    it('should go back to the first page when the page size changes', () => {
        create({ paginated: true, showSearch: false, pageSize: 10 });
        paginator().pageChange.emit(2);
        fixture.detectChanges();

        paginator().pageSizeChange.emit(20);
        fixture.detectChanges();

        expect(paginator().pageSize()).toBe(20);
        expect(paginator().page()).toBe(0);
        expect(element().querySelectorAll('tbody tr')).toHaveLength(20);
    });

    it('should emit a lazy load event with the requested page, sort and column filter', () => {
        const events: TableViewLazyLoadEvent[] = [];
        create({ lazy: true, paginated: true, showSearch: false, pageSize: 10 });
        fixture.componentInstance.onLazyLoad.subscribe((event) => events.push(event));

        paginator().pageChange.emit(1);
        element().querySelector<HTMLButtonElement>('th button')!.click();
        const filter = element().querySelector<HTMLInputElement>('th input')!;
        filter.value = 'Student 07';
        filter.dispatchEvent(new Event('input'));

        expect(events[0]).toEqual(expect.objectContaining({ first: 10, rows: 10 }));
        expect(events[1]).toEqual(expect.objectContaining({ first: 0, sortField: 'name', sortOrder: 1 }));
        expect(events[2]).toEqual(expect.objectContaining({ first: 0, filters: { name: { value: 'Student 07', matchMode: 'contains' } } }));
    });

    it('should filter the client rows by the text of a column filter', () => {
        create({ paginated: false, showSearch: false });
        const filter = element().querySelector<HTMLInputElement>('th input')!;

        filter.value = 'student 1';
        filter.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(renderedNames()).toHaveLength(10);
        expect(renderedNames()[0]).toBe('Student 10');
        expect(renderedNames()[9]).toBe('Student 19');
    });

    it('should select and deselect all visible rows with the header checkbox', () => {
        create({ paginated: true, showSearch: false, pageSize: 10, selectionMode: 'multiple' });
        const selected: unknown[] = [];
        fixture.componentInstance.onRowSelect.subscribe((selection) => selected.push(selection));
        const headerCheckbox = () => element().querySelector<HTMLInputElement>('thead input[type="checkbox"]')!;

        headerCheckbox().click();
        fixture.detectChanges();

        expect(fixture.componentInstance.tableSelection).toEqual(manyStudents.slice(0, 10));
        expect(element().querySelectorAll('tbody tr[data-selected]')).toHaveLength(10);

        headerCheckbox().click();
        fixture.detectChanges();

        expect(fixture.componentInstance.tableSelection).toEqual([]);
        expect(selected).toHaveLength(2);
    });

    it('should keep a single selection when a radio button of a row is chosen', () => {
        create({ paginated: false, showSearch: false, selectionMode: 'single' }, manyStudents.slice(0, 3));

        element().querySelectorAll<HTMLInputElement>('tbody input[type="radio"]')[1].click();
        fixture.detectChanges();

        expect(fixture.componentInstance.tableSelection).toEqual(manyStudents[1]);
        expect(element().querySelectorAll('tbody tr[data-selected]')).toHaveLength(1);
    });
});

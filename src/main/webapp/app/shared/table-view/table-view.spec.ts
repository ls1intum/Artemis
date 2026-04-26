import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TableLazyLoadEvent, TablePageEvent } from 'primeng/table';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ColumnDef, TableViewComponent, TableViewOptions } from './table-view';

interface TestData {
    id: number;
    name: string;
    value: number;
}

describe('TableViewComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TableViewComponent<TestData>;
    let fixture: ComponentFixture<TableViewComponent<TestData>>;

    const mockColumns: ColumnDef<TestData>[] = [
        { field: 'id', header: 'ID', width: '100px', sort: true },
        { field: 'name', header: 'Name', width: '200px', sort: true, filter: true, filterType: 'text' },
        { field: 'value', header: 'Value', width: '150px' },
    ];

    const mockData: TestData[] = [
        { id: 1, name: 'Item 1', value: 100 },
        { id: 2, name: 'Item 2', value: 200 },
        { id: 3, name: 'Item 3', value: 300 },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TableViewComponent],
        })
            .overrideTemplate(TableViewComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TableViewComponent<TestData>);
        component = fixture.componentInstance;

        // Set required inputs
        fixture.componentRef.setInput('cols', mockColumns);
        fixture.componentRef.setInput('vals', mockData);
        fixture.componentRef.setInput('totalRows', 3);

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllTimers();
        vi.useRealTimers();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with inputs', () => {
        expect(component.cols()).toEqual(mockColumns);
        expect(component.vals()).toEqual(mockData);
        expect(component.totalRows()).toBe(3);
    });

    it('should have default loading state as false', () => {
        expect(component.loading()).toBe(false);
    });

    describe('resolvedOptions', () => {
        it('should use all defaults when options is empty', () => {
            const resolved = component['resolvedOptions']();
            expect(resolved.lazy).toBe(true);
            expect(resolved.paginated).toBe(true);
            expect(resolved.striped).toBe(false);
            expect(resolved.selectionMode).toBeUndefined();
            expect(resolved.dataKey).toBe('id');
            expect(resolved.tableStyle).toEqual({ 'min-width': '50rem' });
            expect(resolved.showCurrentPageReport).toBe(true);
            expect(resolved.pageSize).toBe(50);
            expect(resolved.pageSizeOptions).toEqual([10, 20, 50, 100, 200]);
            expect(resolved.showSearch).toBe(true);
            expect(resolved.emptyMessageTranslation).toBe('artemisApp.dataTable.search.noResults');
        });

        it('should merge partial options onto defaults', () => {
            fixture.componentRef.setInput('options', { striped: true, pageSize: 20 });
            const resolved = component['resolvedOptions']();
            expect(resolved.striped).toBe(true);
            expect(resolved.pageSize).toBe(20);
            // Unset fields remain at defaults
            expect(resolved.lazy).toBe(true);
            expect(resolved.paginated).toBe(true);
            expect(resolved.emptyMessageTranslation).toBe('artemisApp.dataTable.search.noResults');
        });

        it('should apply a full options override', () => {
            const fullOptions: TableViewOptions = {
                lazy: false,
                paginated: false,
                striped: true,
                selectionMode: 'single',
                dataKey: 'name',
                tableStyle: { 'min-width': '20rem' },
                showCurrentPageReport: false,
                pageSize: 10,
                pageSizeOptions: [10],
                showSearch: false,
                emptyMessageTranslation: 'custom.message',
                scrollable: true,
                scrollHeight: '400px',
                rowActionsAlignment: 'start',
                searchPlaceholder: 'custom.placeholder',
            };
            fixture.componentRef.setInput('options', fullOptions);
            const resolved = component['resolvedOptions']();
            expect(resolved.lazy).toBe(false);
            expect(resolved.paginated).toBe(false);
            expect(resolved.striped).toBe(true);
            expect(resolved.selectionMode).toBe('single');
            expect(resolved.dataKey).toBe('name');
            expect(resolved.tableStyle).toEqual({ 'min-width': '20rem' });
            expect(resolved.showCurrentPageReport).toBe(false);
            expect(resolved.pageSize).toBe(10);
            expect(resolved.pageSizeOptions).toEqual([10]);
            expect(resolved.showSearch).toBe(false);
            expect(resolved.emptyMessageTranslation).toBe('custom.message');
            expect(resolved.scrollable).toBe(true);
            expect(resolved.scrollHeight).toBe('400px');
            expect(resolved.rowActionsAlignment).toBe('start');
            expect(resolved.searchPlaceholder).toBe('custom.placeholder');
        });

        it('should use default scrollable, scrollHeight, rowActionsAlignment, searchPlaceholder', () => {
            const resolved = component['resolvedOptions']();
            expect(resolved.scrollable).toBe(false);
            expect(resolved.scrollHeight).toBeUndefined();
            expect(resolved.rowActionsAlignment).toBe('end');
            expect(resolved.searchPlaceholder).toBe('artemisApp.course.exercise.search.searchPlaceholder');
        });

        it('should set pageSizeOptions to undefined when hidePageSizeOptions is true', () => {
            fixture.componentRef.setInput('options', { hidePageSizeOptions: true });
            const resolved = component['resolvedOptions']();
            expect(resolved.pageSizeOptions).toBeUndefined();
        });

        it('should use pageSizeOptions from options when hidePageSizeOptions is false', () => {
            fixture.componentRef.setInput('options', { hidePageSizeOptions: false, pageSizeOptions: [5, 25] });
            const resolved = component['resolvedOptions']();
            expect(resolved.pageSizeOptions).toEqual([5, 25]);
        });
    });

    describe('effectiveTotalRows', () => {
        it('should use totalRows when provided', () => {
            fixture.componentRef.setInput('totalRows', 42);
            expect(component['effectiveTotalRows']()).toBe(42);
        });

        it('should fall back to vals().length when totalRows is undefined', () => {
            fixture.componentRef.setInput('totalRows', undefined);
            expect(component['effectiveTotalRows']()).toBe(mockData.length);
        });
    });

    describe('effectivePageSize', () => {
        it('should use options.pageSize as the initial page size', () => {
            expect(component['effectivePageSize']()).toBe(50);
        });

        it('should reflect a custom pageSize from options', () => {
            fixture.componentRef.setInput('options', { pageSize: 20 });
            expect(component['effectivePageSize']()).toBe(20);
        });

        it('should be updated by pageChange', () => {
            const pageEvent: TablePageEvent = { first: 0, rows: 10 };
            component.pageChange(pageEvent);
            expect(component['effectivePageSize']()).toBe(10);
        });

        it('should override options.pageSize after pageChange', () => {
            fixture.componentRef.setInput('options', { pageSize: 20 });
            expect(component['effectivePageSize']()).toBe(20);
            component.pageChange({ first: 0, rows: 100 });
            expect(component['effectivePageSize']()).toBe(100);
        });
    });

    it('should compute item range begin correctly', () => {
        // Default state: first=0, totalRows=3
        expect(component.itemRangeBegin()).toBe(1);

        // When total rows is 0, should be 0
        fixture.componentRef.setInput('totalRows', 0);
        expect(component.itemRangeBegin()).toBe(0);

        // When total rows > 0
        fixture.componentRef.setInput('totalRows', 10);
        expect(component.itemRangeBegin()).toBe(1);
    });

    it('should compute item range end correctly', () => {
        // Default state: first=0, pageSize=50, totalRows=3
        expect(component.itemRangeEnd()).toBe(3);

        // Change page size via pageChange
        component.pageChange({ first: 0, rows: 2 });
        expect(component.itemRangeEnd()).toBe(2);
    });

    it('should compute item range begin from vals length when totalRows is undefined', () => {
        fixture.componentRef.setInput('totalRows', undefined);
        expect(component.itemRangeBegin()).toBe(1);
        expect(component.itemRangeEnd()).toBe(mockData.length);
    });

    it('should build renderer params correctly', () => {
        const rowIndex = 1;
        const col: ColumnDef<TestData> = { field: 'name', header: 'Name' };

        const params = component.buildRendererParams(mockData[1], col, rowIndex);

        expect(params.data).toEqual(mockData[1]);
        expect(params.col).toEqual(col);
        expect(params.value).toBe('Item 2');
        expect(params.rowIndex).toBe(rowIndex);
    });

    it('should build renderer params with undefined value for non-existent field', () => {
        const col: ColumnDef<TestData> = { field: 'nonexistent' as keyof TestData, header: 'Nonexistent' };
        const params = component.buildRendererParams(mockData[0], col, 0);

        expect(params.value).toBeUndefined();
    });

    it('should build renderer params without field', () => {
        const col: ColumnDef<TestData> = { header: 'No Field' };
        const params = component.buildRendererParams(mockData[0], col, 0);

        expect(params.value).toBeUndefined();
    });

    it('should handle lazy load event', () => {
        const lazyLoadEvent: TableLazyLoadEvent = {
            first: 10,
            rows: 20,
            sortField: 'name',
            sortOrder: 1,
            filters: {},
        };

        const onLazyLoadSpy = vi.fn();
        component.onLazyLoad.subscribe(onLazyLoadSpy);

        component.handleLazyLoad(lazyLoadEvent);

        expect(component['currentFirst']()).toBe(10);
        expect(onLazyLoadSpy).toHaveBeenCalledWith(lazyLoadEvent);
        expect(onLazyLoadSpy).toHaveBeenCalledOnce();
    });

    it('should handle page change event', () => {
        const pageEvent: TablePageEvent = {
            first: 20,
            rows: 10,
        };

        component.pageChange(pageEvent);

        expect(component['effectivePageSize']()).toBe(10);
        expect(component['currentFirst']()).toBe(20);
    });

    it('should support loading input', () => {
        expect(component.loading()).toBe(false);

        fixture.componentRef.setInput('loading', true);
        expect(component.loading()).toBe(true);
    });

    it('should support table actions template input', () => {
        expect(component.tableActions()).toBeNull();

        const mockTemplate = { template: 'mock' } as any;
        fixture.componentRef.setInput('tableActions', mockTemplate);

        expect(component.tableActions()).toEqual(mockTemplate);
    });

    it('should support row actions template input', () => {
        expect(component.rowActions()).toBeNull();

        const mockTemplate = { template: 'mock' } as any;
        fixture.componentRef.setInput('rowActions', mockTemplate);

        expect(component.rowActions()).toEqual(mockTemplate);
    });

    it('should emit onRowSelect events', () => {
        const onRowSelectSpy = vi.fn();
        component.onRowSelect.subscribe(onRowSelectSpy);

        const testData = mockData[0];
        component.onRowSelect.emit(testData);

        expect(onRowSelectSpy).toHaveBeenCalledWith(testData);
        expect(onRowSelectSpy).toHaveBeenCalledOnce();
    });

    it('should handle selectedRow property', () => {
        expect(component.selectedRow).toBeUndefined();

        component.selectedRow = mockData[1];
        expect(component.selectedRow).toEqual(mockData[1]);
    });

    it('should debounce global search - single search', () => {
        vi.useFakeTimers();

        const mockTable = { first: 0, filterGlobal: vi.fn() };
        // Mock the dt viewChild
        vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);

        component.onGlobalSearch('test search');

        // Should not have called filterGlobal yet
        expect(mockTable.filterGlobal).not.toHaveBeenCalled();

        // Wait for debounce
        vi.advanceTimersByTime(TableViewComponent['SEARCH_DEBOUNCE_MS']);

        expect(mockTable.filterGlobal).toHaveBeenCalledWith('test search', 'contains');
        expect(mockTable.first).toBe(0);
    });

    it('should debounce global search - cancel previous search', () => {
        vi.useFakeTimers();

        const mockTable = { first: 0, filterGlobal: vi.fn() };
        vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);

        // First search
        component.onGlobalSearch('first search');
        vi.advanceTimersByTime(100);

        // Second search before timeout expires
        component.onGlobalSearch('second search');
        vi.advanceTimersByTime(100);

        // At this point, total time is 200, but debounce is 300
        expect(mockTable.filterGlobal).not.toHaveBeenCalled();

        // Wait for debounce to complete
        vi.advanceTimersByTime(200);

        // Should only call filterGlobal once with the second search term
        expect(mockTable.filterGlobal).toHaveBeenCalledTimes(1);
        expect(mockTable.filterGlobal).toHaveBeenCalledWith('second search', 'contains');
    });

    it('should trim and lowercase search value', () => {
        vi.useFakeTimers();

        const mockTable = { first: 0, filterGlobal: vi.fn() };
        vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);

        component.onGlobalSearch('  TEST SEARCH  ');

        vi.advanceTimersByTime(TableViewComponent['SEARCH_DEBOUNCE_MS']);

        expect(mockTable.filterGlobal).toHaveBeenCalledWith('test search', 'contains');
    });

    it('should access currentFirst private property', () => {
        expect(component['currentFirst']()).toBe(0);

        component['currentFirst'].set(5);
        expect(component['currentFirst']()).toBe(5);
    });

    it('should maintain correct item range with updated total rows', () => {
        // Initial state
        expect(component.itemRangeBegin()).toBe(1);
        expect(component.itemRangeEnd()).toBe(3);

        // Update total rows
        fixture.componentRef.setInput('totalRows', 10);
        expect(component.itemRangeBegin()).toBe(1);
        expect(component.itemRangeEnd()).toBe(10);

        // Change page size via pageChange
        component.pageChange({ first: 0, rows: 5 });
        expect(component.itemRangeEnd()).toBe(5);
    });

    it('should handle empty data', () => {
        fixture.componentRef.setInput('vals', []);
        fixture.componentRef.setInput('totalRows', 0);

        expect(component.vals()).toEqual([]);
        expect(component.totalRows()).toBe(0);
        expect(component.itemRangeBegin()).toBe(0);
        expect(component.itemRangeEnd()).toBe(0);
    });

    it('should build params for each column in data row', () => {
        const rowIndex = 0;
        const paramsForEachCol = mockColumns.map((col) => component.buildRendererParams(mockData[0], col, rowIndex));

        expect(paramsForEachCol).toHaveLength(3);
        expect(paramsForEachCol[0].value).toBe(1); // id
        expect(paramsForEachCol[1].value).toBe('Item 1'); // name
        expect(paramsForEachCol[2].value).toBe(100); // value
    });
});

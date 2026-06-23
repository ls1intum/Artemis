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
            expect(resolved.searchPlaceholder).toBe('custom.placeholder');
        });

        it('should use default scrollable, scrollHeight, searchPlaceholder', () => {
            const resolved = component['resolvedOptions']();
            expect(resolved.scrollable).toBe(false);
            expect(resolved.scrollHeight).toBeUndefined();
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
        expect((component as any).itemRangeBegin()).toBe(1);

        // When total rows is 0, should be 0
        fixture.componentRef.setInput('totalRows', 0);
        expect((component as any).itemRangeBegin()).toBe(0);

        // When total rows > 0
        fixture.componentRef.setInput('totalRows', 10);
        expect((component as any).itemRangeBegin()).toBe(1);
    });

    it('should compute item range end correctly', () => {
        // Default state: first=0, pageSize=50, totalRows=3
        expect((component as any).itemRangeEnd()).toBe(3);

        // Change page size via pageChange
        component.pageChange({ first: 0, rows: 2 });
        expect((component as any).itemRangeEnd()).toBe(2);
    });

    it('should compute item range begin from vals length when totalRows is undefined', () => {
        fixture.componentRef.setInput('totalRows', undefined);
        expect((component as any).itemRangeBegin()).toBe(1);
        expect((component as any).itemRangeEnd()).toBe(mockData.length);
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

    describe('reset', () => {
        it('should be a no-op in non-lazy mode', () => {
            fixture.componentRef.setInput('options', { lazy: false });
            const mockTable = { first: 5, filters: {}, sortField: undefined, sortOrder: undefined };
            vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);
            const onLazyLoadSpy = vi.fn();
            component.onLazyLoad.subscribe(onLazyLoadSpy);

            component.reset();

            expect(onLazyLoadSpy).not.toHaveBeenCalled();
        });

        it('should reset pagination state and emit a lazy load event at page 0 with cleared filters', () => {
            const mockTable = { first: 50, filters: { global: { value: 'test', matchMode: 'contains' } }, sortField: 'name', sortOrder: 1 };
            vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);
            component.pageChange({ first: 50, rows: 10 });
            const onLazyLoadSpy = vi.fn();
            component.onLazyLoad.subscribe(onLazyLoadSpy);

            component.reset();

            expect(component['currentFirst']()).toBe(0);
            expect(component['currentPageSizeOverride']()).toBeUndefined();
            expect(mockTable.first).toBe(0);
            expect(mockTable.filters).toEqual({});
            expect(onLazyLoadSpy).toHaveBeenCalledOnce();
            const event = onLazyLoadSpy.mock.calls[0][0] as TableLazyLoadEvent;
            expect(event.first).toBe(0);
            expect(event.globalFilter).toBeNull();
            expect(event.filters).toEqual({});
        });

        it('should cancel any pending search debounce and fire exactly once', () => {
            vi.useFakeTimers();
            const mockTable = { first: 0, filters: {}, sortField: undefined, sortOrder: undefined, filterGlobal: vi.fn() };
            vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);
            const onLazyLoadSpy = vi.fn();
            component.onLazyLoad.subscribe(onLazyLoadSpy);

            component.onGlobalSearch('pending search');
            component.reset();

            expect(onLazyLoadSpy).toHaveBeenCalledOnce();

            vi.advanceTimersByTime(500);
            expect(onLazyLoadSpy).toHaveBeenCalledOnce(); // no second fire from the cancelled debounce
        });
    });

    describe('reload', () => {
        it('should be a no-op in non-lazy mode', () => {
            fixture.componentRef.setInput('options', { lazy: false });
            const mockTable = { first: 0, filters: {}, sortField: undefined, sortOrder: undefined };
            vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);
            const onLazyLoadSpy = vi.fn();
            component.onLazyLoad.subscribe(onLazyLoadSpy);

            component.reload();

            expect(onLazyLoadSpy).not.toHaveBeenCalled();
        });

        it('should reset to page 0 and emit a lazy load event preserving sort and active filter', () => {
            const mockTable = {
                first: 50,
                filters: { global: { value: 'my search', matchMode: 'contains' } },
                sortField: 'name',
                sortOrder: -1,
            };
            vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);
            component.pageChange({ first: 50, rows: 10 });
            const onLazyLoadSpy = vi.fn();
            component.onLazyLoad.subscribe(onLazyLoadSpy);

            component.reload();

            expect(component['currentFirst']()).toBe(0);
            expect(mockTable.first).toBe(0);
            expect(onLazyLoadSpy).toHaveBeenCalledOnce();
            const event = onLazyLoadSpy.mock.calls[0][0] as TableLazyLoadEvent;
            expect(event.first).toBe(0);
            expect(event.sortField).toBe('name');
            expect(event.sortOrder).toBe(-1);
            expect(event.globalFilter).toBe('my search');
        });

        it('should use null for globalFilter when no search is active', () => {
            const mockTable = { first: 0, filters: {}, sortField: undefined, sortOrder: undefined };
            vi.spyOn(component, 'dt').mockReturnValue(mockTable as any);
            const onLazyLoadSpy = vi.fn();
            component.onLazyLoad.subscribe(onLazyLoadSpy);

            component.reload();

            const event = onLazyLoadSpy.mock.calls[0][0] as TableLazyLoadEvent;
            expect(event.globalFilter).toBeNull();
        });
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

    it('should handle tableSelection property', () => {
        expect(component.tableSelection).toBeUndefined();

        component.tableSelection = mockData[1];
        expect(component.tableSelection).toEqual(mockData[1]);
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
        expect((component as any).itemRangeBegin()).toBe(1);
        expect((component as any).itemRangeEnd()).toBe(3);

        // Update total rows
        fixture.componentRef.setInput('totalRows', 10);
        expect((component as any).itemRangeBegin()).toBe(1);
        expect((component as any).itemRangeEnd()).toBe(10);

        // Change page size via pageChange
        component.pageChange({ first: 0, rows: 5 });
        expect((component as any).itemRangeEnd()).toBe(5);
    });

    it('should handle empty data', () => {
        fixture.componentRef.setInput('vals', []);
        fixture.componentRef.setInput('totalRows', 0);

        expect(component.vals()).toEqual([]);
        expect(component.totalRows()).toBe(0);
        expect((component as any).itemRangeBegin()).toBe(0);
        expect((component as any).itemRangeEnd()).toBe(0);
    });

    it('should build params for each column in data row', () => {
        const rowIndex = 0;
        const paramsForEachCol = mockColumns.map((col) => component.buildRendererParams(mockData[0], col, rowIndex));

        expect(paramsForEachCol).toHaveLength(3);
        expect(paramsForEachCol[0].value).toBe(1); // id
        expect(paramsForEachCol[1].value).toBe('Item 1'); // name
        expect(paramsForEachCol[2].value).toBe(100); // value
    });

    describe('resolvedOptions - sort and global-filter additions', () => {
        it('should default globalFilterFields/initialSortField to undefined and initialSortOrder to 1', () => {
            const resolved = component['resolvedOptions']();
            expect(resolved.globalFilterFields).toBeUndefined();
            expect(resolved.initialSortField).toBeUndefined();
            expect(resolved.initialSortOrder).toBe(1);
        });

        it('should merge globalFilterFields and initial sort settings from options', () => {
            fixture.componentRef.setInput('options', { globalFilterFields: ['name', 'value'], initialSortField: 'name', initialSortOrder: -1 });
            const resolved = component['resolvedOptions']();
            expect(resolved.globalFilterFields).toEqual(['name', 'value']);
            expect(resolved.initialSortField).toBe('name');
            expect(resolved.initialSortOrder).toBe(-1);
        });
    });

    describe('dot-path value resolution', () => {
        it('should resolve a nested dot-path field via lodash get', () => {
            const nested = { id: 1, owner: { name: 'Alice' } } as unknown as TestData;
            const params = component.buildRendererParams(nested, { field: 'owner.name' }, 0);
            expect(params.value).toBe('Alice');
        });

        it('should resolve array-index fields (tuple access)', () => {
            const tuple = ['key', 'value'] as unknown as TestData;
            expect(component.buildRendererParams(tuple, { field: '0' }, 0).value).toBe('key');
            expect(component.buildRendererParams(tuple, { field: '1' }, 0).value).toBe('value');
        });

        it('should yield undefined for a missing nested path', () => {
            const params = component.buildRendererParams(mockData[0], { field: 'owner.name' }, 0);
            expect(params.value).toBeUndefined();
        });
    });

    describe('hasCustomSort', () => {
        const comparator = (a: TestData, b: TestData) => a.value - b.value;

        it('should be false when no column defines a comparator', () => {
            expect(component['hasCustomSort']()).toBe(false);
        });

        it('should be true when a column defines a comparator in non-lazy mode', () => {
            fixture.componentRef.setInput('options', { lazy: false });
            fixture.componentRef.setInput('cols', [{ field: 'value', sort: true, sortComparator: comparator }]);
            expect(component['hasCustomSort']()).toBe(true);
        });

        it('should be false in lazy mode even when a column defines a comparator', () => {
            fixture.componentRef.setInput('options', { lazy: true });
            fixture.componentRef.setInput('cols', [{ field: 'value', sort: true, sortComparator: comparator }]);
            expect(component['hasCustomSort']()).toBe(false);
        });
    });

    describe('onCustomSort', () => {
        const byValue = (a: TestData, b: TestData) => a.value - b.value;
        const unsorted = (): TestData[] => [
            { id: 1, name: 'a', value: 300 },
            { id: 2, name: 'b', value: 100 },
            { id: 3, name: 'c', value: 200 },
        ];

        it('should sort in place using a column comparator (ascending)', () => {
            fixture.componentRef.setInput('cols', [{ field: 'value', sort: true, sortComparator: byValue }]);
            const data = unsorted();
            component.onCustomSort({ data, field: 'value', order: 1 });
            expect(data.map((d) => d.value)).toEqual([100, 200, 300]);
        });

        it('should negate the comparator result for descending order', () => {
            fixture.componentRef.setInput('cols', [{ field: 'value', sort: true, sortComparator: byValue }]);
            const data = unsorted();
            component.onCustomSort({ data, field: 'value', order: -1 });
            expect(data.map((d) => d.value)).toEqual([300, 200, 100]);
        });

        it('should fall back to numeric field comparison for columns without a comparator', () => {
            const data = unsorted();
            component.onCustomSort({ data, field: 'value', order: 1 });
            expect(data.map((d) => d.value)).toEqual([100, 200, 300]);
        });

        it('should fall back to localeCompare for string fields', () => {
            const data: TestData[] = [
                { id: 1, name: 'Charlie', value: 1 },
                { id: 2, name: 'alpha', value: 2 },
                { id: 3, name: 'Bravo', value: 3 },
            ];
            component.onCustomSort({ data, field: 'name', order: 1 });
            expect(data.map((d) => d.name)).toEqual(['alpha', 'Bravo', 'Charlie']);
        });

        it('should sort nullish values last in the ascending fallback', () => {
            const data: TestData[] = [
                { id: 1, name: 'a', value: 300 },
                { id: 2, name: 'b', value: undefined as unknown as number },
                { id: 3, name: 'c', value: 100 },
            ];
            component.onCustomSort({ data, field: 'value', order: 1 });
            expect(data.map((d) => d.value)).toEqual([100, 300, undefined]);
        });

        it('should resolve dot-path fields in the fallback comparison', () => {
            fixture.componentRef.setInput('cols', [{ field: 'owner.name', sort: true }]);
            const data = [
                { id: 1, owner: { name: 'Charlie' } },
                { id: 2, owner: { name: 'alpha' } },
                { id: 3, owner: { name: 'Bravo' } },
            ] as unknown as TestData[];
            component.onCustomSort({ data, field: 'owner.name', order: 1 });
            expect(data.map((d) => (d as unknown as { owner: { name: string } }).owner.name)).toEqual(['alpha', 'Bravo', 'Charlie']);
        });

        it('should default to ascending order when order is undefined', () => {
            const data: TestData[] = [
                { id: 1, name: 'a', value: 200 },
                { id: 2, name: 'b', value: 100 },
            ];
            component.onCustomSort({ data, field: 'value' });
            expect(data.map((d) => d.value)).toEqual([100, 200]);
        });

        it('should be a no-op when event.data is undefined', () => {
            expect(() => component.onCustomSort({ data: undefined as unknown as TestData[], field: 'value', order: 1 })).not.toThrow();
        });
    });

    describe('rowClass input', () => {
        it('should default to null', () => {
            expect(component.rowClass()).toBeNull();
        });

        it('should expose the provided row-class function', () => {
            const rowClassFn = (row: TestData) => (row.id === 1 ? 'highlight' : '');
            fixture.componentRef.setInput('rowClass', rowClassFn);
            expect(component.rowClass()?.(mockData[0])).toBe('highlight');
            expect(component.rowClass()?.(mockData[1])).toBe('');
        });
    });

    describe('onFilter (filtered row count)', () => {
        it('should emit the number of rows remaining after a client-side filter', () => {
            const spy = vi.fn();
            component.filteredRowsChange.subscribe(spy);
            component.onFilter({ filteredValue: [mockData[0], mockData[1]] });
            expect(spy).toHaveBeenCalledWith(2);
        });

        it('should emit the full row count when the filter is cleared (filteredValue is null)', () => {
            const spy = vi.fn();
            component.filteredRowsChange.subscribe(spy);
            component.onFilter({ filteredValue: null });
            expect(spy).toHaveBeenCalledWith(mockData.length);
        });
    });
});

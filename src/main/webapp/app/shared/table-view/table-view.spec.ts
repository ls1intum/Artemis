import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TableLazyLoadEvent, TablePageEvent } from 'primeng/table';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ColumnDef, TableView } from './table-view';

interface TestData {
    id: number;
    name: string;
    value: number;
}

describe('TableView', () => {
    setupTestBed({ zoneless: true });

    let component: TableView<TestData>;
    let fixture: ComponentFixture<TableView<TestData>>;

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
            imports: [TableView],
        })
            .overrideTemplate(TableView, '')
            .compileComponents();

        fixture = TestBed.createComponent(TableView<TestData>);
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

    it('should initialize with required inputs', () => {
        expect(component.cols()).toEqual(mockColumns);
        expect(component.vals()).toEqual(mockData);
        expect(component.totalRows()).toBe(3);
        expect(component.pageSize()).toBe(50);
    });

    it('should have default page size options', () => {
        expect(component.pageSizeOptions()).toEqual([10, 20, 50, 100, 200]);
    });

    it('should have default loading state as false', () => {
        expect(component.loading()).toBe(false);
    });

    it('should have default empty message translation', () => {
        expect(component.emptyMessageTranslation()).toBe('artemisApp.dataTable.search.noResults');
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

        // Change page size to 2
        component.pageSize.set(2);
        expect(component.itemRangeEnd()).toBe(2);
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

        expect(component.pageSize()).toBe(10);
        expect(component['currentFirst']()).toBe(20);
    });

    it('should update page size via model', () => {
        expect(component.pageSize()).toBe(50);

        component.pageSize.set(100);
        expect(component.pageSize()).toBe(100);
    });

    it('should support custom page size options', () => {
        const customOptions = [5, 15, 25, 50];
        fixture.componentRef.setInput('pageSizeOptions', customOptions);

        expect(component.pageSizeOptions()).toEqual(customOptions);
    });

    it('should support loading input', () => {
        expect(component.loading()).toBe(false);

        fixture.componentRef.setInput('loading', true);
        expect(component.loading()).toBe(true);
    });

    it('should support custom empty message translation', () => {
        expect(component.emptyMessageTranslation()).toBe('artemisApp.dataTable.search.noResults');

        fixture.componentRef.setInput('emptyMessageTranslation', 'custom.message');
        expect(component.emptyMessageTranslation()).toBe('custom.message');
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
        vi.advanceTimersByTime(TableView['SEARCH_DEBOUNCE_MS']);

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

        vi.advanceTimersByTime(TableView['SEARCH_DEBOUNCE_MS']);

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

        // Update page size
        component.pageSize.set(5);
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

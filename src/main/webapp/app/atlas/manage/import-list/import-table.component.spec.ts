import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PagingService } from 'app/exercise/services/paging.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Observable, Subject, of } from 'rxjs';
import { SearchResult, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ImportTableComponent } from 'app/atlas/manage/import-list/import-table.component';
import { Column } from 'app/shared-ui/import/import.component';
import { PaginatorState } from 'primeng/paginator';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ImportTableComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ImportTableComponent<Course>;
    let fixture: ComponentFixture<ImportTableComponent<Course>>;

    let pagingService: PagingService<Course>;
    let alertService: AlertService;
    let searchSpy: ReturnType<typeof vi.spyOn>;

    const columns: Column<Course>[] = [
        {
            name: 'TITLE',
            getProperty: (entity: Course) => entity.title,
        },
        {
            name: 'SHORT_NAME',
            getProperty: (entity: Course) => entity.shortName,
        },
    ];

    let objectList: Course[] = [];

    let searchResult: SearchResult<Course>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImportTableComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: PagingService,
                    useClass: class MockPagingService extends PagingService<Course> {
                        search = vi.fn();
                    },
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
            ],
        }).compileComponents();

        objectList = [
            { id: 1, title: 'Object 01', shortName: 'O1' },
            { id: 2, title: 'Object 02', shortName: 'O2' },
            { id: 3, title: 'Object 03', shortName: 'O3' },
            { id: 4, title: 'Object 04', shortName: 'O4' },
            { id: 5, title: 'Object 05', shortName: 'O5' },
            { id: 6, title: 'Object 06', shortName: 'O6' },
            { id: 7, title: 'Object 07', shortName: 'O7' },
            { id: 8, title: 'Object 08', shortName: 'O8' },
            { id: 9, title: 'Object 09', shortName: 'O9' },
            { id: 10, title: 'Object 10', shortName: '10' },
        ];

        searchResult = <SearchResult<Course>>{
            resultsOnPage: objectList,
            numberOfPages: 2,
        };

        pagingService = TestBed.inject(PagingService);
        alertService = TestBed.inject(AlertService);
        searchSpy = vi.spyOn(pagingService, 'search').mockReturnValue(of(searchResult));

        fixture = TestBed.createComponent(ImportTableComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('columns', columns);
        fixture.componentRef.setInput('columnBaseTranslationKey', 'translationKey');
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.columns()).toEqual(columns);
    });

    it('should load and display data correctly in table', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        const tableRows = fixture.debugElement.nativeElement.querySelectorAll('tbody tr');
        const tableColumns = tableRows[0].querySelectorAll('td');

        expect(component.resultsOnPage()).toEqual(objectList);
        expect(tableRows).toHaveLength(objectList.length);
        expect(tableColumns).toHaveLength(columns.length + 2);
        expect(searchSpy).toHaveBeenCalledExactlyOnceWith({
            page: 1,
            pageSize: 10,
            sortingOrder: SortingOrder.ASCENDING,
            sortedColumn: 'ID',
            searchTerm: '',
        });
    });

    it('should set isLoading correctly on data load', async () => {
        const isLoadingSpy = vi.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should search data', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const searchField = fixture.debugElement.nativeElement.querySelector('#import-objects-search');
        searchField.value = 'Object 01';
        searchField.dispatchEvent(new Event('input'));

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(component.searchTerm()).toBe('Object 01');
        expect(searchSpy).toHaveBeenCalled();
    });

    it('should select row', async () => {
        const onRowSelectionSpy = vi.spyOn(component.onRowSelection, 'emit');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        const selectButton = fixture.debugElement.nativeElement.querySelectorAll('tbody tr:nth-child(1) button')[0];

        selectButton.click();

        expect(onRowSelectionSpy).toHaveBeenCalledExactlyOnceWith(objectList[0]);
    });

    it('should change page via the paginator', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(component.page()).toBe(1);

        // PrimeNG paginator emits a 0-indexed page; page 1 (0-indexed) is the component's page 2.
        component['onPageChange']({ page: 1 } as PaginatorState);

        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        expect(component.page()).toBe(2);
        expect(searchSpy).toHaveBeenLastCalledWith({
            page: 2,
            pageSize: 10,
            sortingOrder: SortingOrder.ASCENDING,
            sortedColumn: 'ID',
            searchTerm: '',
        });
    });

    it('should sort TITLE column ASCENDING', async () => {
        await loadDataAndClickColumn(2, 1, 'TITLE', SortingOrder.ASCENDING, 'Object 01');
    });

    it('should sort TITLE column DESCENDING', async () => {
        const invertedObjectList = objectList.reverse();
        searchSpy.mockReturnValueOnce(of({ ...searchResult, resultsOnPage: invertedObjectList }));

        await loadDataAndClickColumn(2, 2, 'TITLE', SortingOrder.DESCENDING, 'Object 10');
    });

    it('should sort ID column DESCENDING', async () => {
        const invertedObjectList = objectList.reverse();
        searchSpy.mockReturnValue(of({ ...searchResult, resultsOnPage: invertedObjectList }));

        await loadDataAndClickColumn(1, 1, 'ID', SortingOrder.DESCENDING, 'Object 10');
    });

    it('should show alert on data load error', async () => {
        searchSpy.mockReturnValue(new Observable((observer) => observer.error('Error message')));
        const errorSpy = vi.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should discard a stale out-of-order search response', async () => {
        // Reproduces the race the guard in loadData() fixes: an earlier request (e.g. the initial unfiltered
        // load) and a newer request (a search term the user typed) are both in flight. The newer request
        // resolves first and is applied; the slower earlier request must NOT overwrite it afterwards.
        const loadData = () => (component as unknown as { loadData: () => Promise<void> }).loadData();
        const staleResponse = new Subject<SearchResult<Course>>();
        const currentResponse = new Subject<SearchResult<Course>>();
        searchSpy.mockReturnValueOnce(staleResponse).mockReturnValueOnce(currentResponse);

        // Earlier request issued with the default empty search term.
        const stalePromise = loadData();
        // The user types: a newer request is issued with a different search term.
        component.searchTerm.set('Object 02');
        const currentPromise = loadData();

        // The newer request resolves first and is applied.
        currentResponse.next(<SearchResult<Course>>{ resultsOnPage: [objectList[1]], numberOfPages: 1 });
        currentResponse.complete();
        await currentPromise;
        expect(component.resultsOnPage()).toEqual([objectList[1]]);

        // The slower earlier request resolves afterwards and must be discarded, not applied.
        staleResponse.next(<SearchResult<Course>>{ resultsOnPage: objectList, numberOfPages: 2 });
        staleResponse.complete();
        await stalePromise;
        expect(component.resultsOnPage()).toEqual([objectList[1]]);
    });

    async function loadDataAndClickColumn(columnIndex: number, timesClicking: number, compareSortedColumn: string, compareSortingOrder: SortingOrder, compareCellContent: string) {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        const titleColumnHeader = fixture.debugElement.nativeElement.querySelector('thead th:nth-child(' + columnIndex + ') div');

        expect(component.sortedColumn()).toBe('ID');
        expect(component.sortingOrder()).toBe(SortingOrder.ASCENDING);

        for (let i = 0; i < timesClicking; i++) {
            titleColumnHeader.click();
        }

        expect(component.sortedColumn()).toBe(compareSortedColumn);
        expect(component.sortingOrder()).toBe(compareSortingOrder);
        expect(searchSpy).toHaveBeenLastCalledWith({
            page: 1,
            pageSize: 10,
            sortingOrder: compareSortingOrder,
            sortedColumn: compareSortedColumn,
            searchTerm: '',
        });

        const tableRows = fixture.debugElement.nativeElement.querySelectorAll('tbody tr');
        const tableColumns = tableRows[0].querySelectorAll('td');
        expect(tableColumns[1].textContent).toBe(compareCellContent);
    }
});

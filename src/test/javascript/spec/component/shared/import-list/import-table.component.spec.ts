import '@angular/localize/init';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Column, ImportTableComponent } from 'app/shared/import-list/import-table.component';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { Course } from 'app/entities/course.model';
import { Observable, of } from 'rxjs';
import { SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';

describe('ImportTableComponent', () => {
    let component: ImportTableComponent<Course>;
    let fixture: ComponentFixture<ImportTableComponent<Course>>;

    let pagingService: PagingService<Course>;
    let alertService: AlertService;
    let searchSpy: jest.SpyInstance;

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
                        search = jest.fn();
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
        searchSpy = jest.spyOn(pagingService, 'search').mockReturnValue(of(searchResult));

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
        fixture.detectChanges();

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
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

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

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.searchTerm()).toBe('Object 01');
        expect(searchSpy).toHaveBeenLastCalledWith({
            page: 1,
            pageSize: 10,
            sortingOrder: SortingOrder.ASCENDING,
            sortedColumn: 'ID',
            searchTerm: 'Object 01',
        });
    });

    it('should select row', async () => {
        const onRowSelectionSpy = jest.spyOn(component.onRowSelection, 'emit');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const selectButton = fixture.debugElement.nativeElement.querySelectorAll('tbody tr:nth-child(1) button')[0];

        selectButton.click();

        expect(onRowSelectionSpy).toHaveBeenCalledExactlyOnceWith(objectList[0]);
    });

    it('should set page size', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.page()).toBe(1);

        const pageSizeSelect = fixture.debugElement.nativeElement.querySelectorAll('.page-link')[3];

        pageSizeSelect.click();

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

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
        const errorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    async function loadDataAndClickColumn(columnIndex: number, timesClicking: number, compareSortedColumn: string, compareSortingOrder: SortingOrder, compareCellContent: string) {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

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

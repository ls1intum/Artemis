import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Component, inject } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ImportComponent } from 'app/shared-ui/import/import.component';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { SortService } from 'app/foundation/service/sort.service';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { DummyPagingService } from 'test/helpers/mocks/service/dummy-paging-service';
import { vi } from 'vitest';

@Component({ template: '' })
class DummyImportComponent extends ImportComponent<BaseEntity> {
    constructor() {
        const pagingService = inject(DummyPagingService);
        super(pagingService);
    }
}
describe('ImportComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<DummyImportComponent>;
    let comp: DummyImportComponent;
    let pagingService: DummyPagingService;
    let sortService: SortService;
    let searchStub: ReturnType<typeof vi.spyOn>;
    let sortByPropertyStub: ReturnType<typeof vi.spyOn>;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    let searchResult: SearchResult<BaseEntity>;
    let state: SearchTermPageableSearch;

    const content: BaseEntity = { id: 2 };

    beforeEach(() => {
        vi.useFakeTimers();

        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                DummyImportComponent,
                MockComponent(ButtonComponent),
                MockComponent(NgbPagination),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
            ],
            providers: [MockProvider(DummyPagingService), MockProvider(SortService), { provide: DynamicDialogRef, useValue: dialogRef }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DummyImportComponent);
                comp = fixture.componentInstance;
                pagingService = TestBed.inject(DummyPagingService);
                sortService = TestBed.inject(SortService);

                searchStub = vi.spyOn(pagingService, 'search');
                sortByPropertyStub = vi.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    beforeEach(() => {
        searchResult = { numberOfPages: 3, resultsOnPage: [content] };
        state = {
            page: 1,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: 'ID',
            ...searchResult,
        };
        searchStub.mockReturnValue(of(searchResult));
    });

    it('should initialize the content', () => {
        fixture.detectChanges();
        expect(comp.content).toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });

    it('should initialize the subjects', () => {
        const searchSpy = vi.spyOn(comp, 'performSearch' as any);

        fixture.detectChanges();

        expect(searchSpy).toHaveBeenCalledTimes(2);
        expect(searchSpy).toHaveBeenCalledWith(expect.any(Subject), 0);
        expect(searchSpy).toHaveBeenCalledWith(expect.any(Subject), 300);
    });

    const setStateAndCallOnInit = (middleExpectation: () => void) => {
        comp.state = { ...state };
        comp.ngOnInit();
        middleExpectation();
        expect(comp.content).toEqual(searchResult);
        comp.sortRows();
        expect(sortByPropertyStub).toHaveBeenCalledWith(searchResult.resultsOnPage, comp.sortedColumn, comp.listSorting);
    };

    it('should set content to paging result on sort', () => {
        expect(comp.listSorting).toBeFalsy();
        setStateAndCallOnInit(() => {
            comp.listSorting = true;
            vi.advanceTimersByTime(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING }, undefined);
            expect(comp.listSorting).toBeTruthy();
        });
    });

    it('should set content to paging result on pageChange', () => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            vi.advanceTimersByTime(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, page: 5 }, undefined);
            expect(comp.page).toBe(5);
        });
    });

    it('should set content to paging result on search', () => {
        expect(comp.searchTerm).toBe('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenSearchTerm';
            comp.searchTerm = givenSearchTerm;
            vi.advanceTimersByTime(10);
            expect(searchStub).not.toHaveBeenCalled();
            vi.advanceTimersByTime(290);
            expect(searchStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, undefined);
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    });

    it('should set content to paging result on sortedColumn change', () => {
        expect(comp.sortedColumn).toBe('ID');
        setStateAndCallOnInit(() => {
            comp.sortedColumn = 'TITLE';
            vi.advanceTimersByTime(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, sortedColumn: 'TITLE' }, undefined);
            expect(comp.sortedColumn).toBe('TITLE');
        });
    });

    it('should return tracked id', () => {
        expect(comp.trackId(0, content)).toEqual(content.id);
    });

    it('should close the dialog', () => {
        // WHEN
        comp.clear();

        // THEN
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith();
    });

    it('should close the dialog with result', () => {
        // GIVEN
        const exam = { id: 1 } as Exam;
        // WHEN
        comp.selectImport(exam);

        // THEN
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith(exam);
    });

    it('should change the page on active modal', () => {
        const defaultPageSize = 10;
        const numberOfPages = 5;
        const pagingServiceSpy = vi.spyOn(pagingService, 'search');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages } as SearchResult<Exam>));

        fixture.detectChanges();

        let expectedPageNumber = 1;
        comp.onPageChange(expectedPageNumber);
        vi.advanceTimersByTime(0);
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total).toBe(numberOfPages * defaultPageSize);

        expectedPageNumber = 2;
        comp.onPageChange(expectedPageNumber);
        vi.advanceTimersByTime(0);
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total).toBe(numberOfPages * defaultPageSize);

        // Page number should be changed unless it is falsy.
        comp.onPageChange(0);
        vi.advanceTimersByTime(0);
        expect(comp.page).toBe(expectedPageNumber);

        // Number of times onPageChange is called with a truthy value.
        expect(pagingServiceSpy).toHaveBeenCalledTimes(2);
    });

    it('should sort rows with default values', () => {
        const sortServiceSpy = vi.spyOn(sortService, 'sortByProperty');

        fixture.detectChanges();
        comp.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledOnce();
        expect(sortServiceSpy).toHaveBeenCalledWith([], 'ID', false);
    });

    it('should call createOptions when performing a search request', () => {
        // @ts-ignore (the method is protected but exists)
        const createOptionsSpy = vi.spyOn(comp, 'createOptions').mockReturnValue({ test: true });
        setStateAndCallOnInit(() => {
            comp.searchTerm = state.searchTerm = 'newSearchTerm';
            vi.advanceTimersByTime(300);
            expect(searchStub).toHaveBeenCalledWith({ ...state }, { test: true });
            expect(createOptionsSpy).toHaveBeenCalled();
        });
    });

    it('should call onSearchResult after performing a search request', () => {
        // @ts-ignore (the method is protected but exists)
        const onSearchResultSpy = vi.spyOn(comp, 'onSearchResult');
        setStateAndCallOnInit(() => {
            comp.searchTerm = state.searchTerm = 'newSearchTerm';
            vi.advanceTimersByTime(300);
            expect(searchStub).toHaveBeenCalledWith({ ...state }, undefined);
            expect(onSearchResultSpy).toHaveBeenCalled();
        });
    });
});

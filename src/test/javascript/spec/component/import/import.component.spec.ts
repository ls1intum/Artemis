import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgbActiveModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { Exam } from 'app/entities/exam.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ImportComponent } from 'app/shared/import/import.component';
import { BaseEntity } from 'app/shared/model/base-entity';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { MockComponent, MockDirective } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { DummyPagingService } from '../manage/dummy-paging-service';

@Component({ template: '' })
class DummyImportComponent extends ImportComponent<BaseEntity> {
    constructor(router: Router, sortService: SortService, activeModal: NgbActiveModal, pagingService: DummyPagingService) {
        super(router, sortService, activeModal, pagingService);
    }
}
describe('ImportComponent', () => {
    let fixture: ComponentFixture<DummyImportComponent>;
    let comp: DummyImportComponent;
    let pagingService: DummyPagingService;
    let sortService: SortService;
    let searchStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let activeModal: NgbActiveModal;

    let searchResult: SearchResult<BaseEntity>;
    let state: SearchTermPageableSearch;

    const content: BaseEntity = { id: 2 };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockComponent(NgbPagination)],
            declarations: [DummyImportComponent, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DummyImportComponent);
                comp = fixture.componentInstance;
                pagingService = TestBed.inject(DummyPagingService);
                sortService = TestBed.inject(SortService);
                activeModal = TestBed.inject(NgbActiveModal);

                searchStub = jest.spyOn(pagingService, 'search');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
        const searchSpy = jest.spyOn(comp, 'performSearch' as any);

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

    it('should set content to paging result on sort', fakeAsync(() => {
        expect(comp.listSorting).toBeFalse();
        setStateAndCallOnInit(() => {
            comp.listSorting = true;
            tick(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING }, undefined);
            expect(comp.listSorting).toBeTrue();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, page: 5 }, undefined);
            expect(comp.page).toBe(5);
        });
    }));

    it('should set content to paging result on search', fakeAsync(() => {
        expect(comp.searchTerm).toBe('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenSearchTerm';
            comp.searchTerm = givenSearchTerm;
            tick(10);
            expect(searchStub).not.toHaveBeenCalled();
            tick(290);
            expect(searchStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, undefined);
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toBe('ID');
        setStateAndCallOnInit(() => {
            comp.sortedColumn = 'TITLE';
            tick(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, sortedColumn: 'TITLE' }, undefined);
            expect(comp.sortedColumn).toBe('TITLE');
        });
    }));

    it('should return tracked id', () => {
        expect(comp.trackId(0, content)).toEqual(content.id);
    });

    it('should close the active modal', () => {
        const activeModalSpy = jest.spyOn(activeModal, 'dismiss');

        // WHEN
        comp.clear();

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith('cancel');
    });

    it('should close the active modal with result', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'close');
        const exam = { id: 1 } as Exam;
        // WHEN
        comp.selectImport(exam);

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith(exam);
    });

    it('should change the page on active modal', fakeAsync(() => {
        const defaultPageSize = 10;
        const numberOfPages = 5;
        const pagingServiceSpy = jest.spyOn(pagingService, 'search');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages } as SearchResult<Exam>));

        fixture.detectChanges();

        let expectedPageNumber = 1;
        comp.onPageChange(expectedPageNumber);
        tick();
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total).toBe(numberOfPages * defaultPageSize);

        expectedPageNumber = 2;
        comp.onPageChange(expectedPageNumber);
        tick();
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total).toBe(numberOfPages * defaultPageSize);

        // Page number should be changed unless it is falsy.
        comp.onPageChange(0);
        tick();
        expect(comp.page).toBe(expectedPageNumber);

        // Number of times onPageChange is called with a truthy value.
        expect(pagingServiceSpy).toHaveBeenCalledTimes(2);
    }));

    it('should sort rows with default values', () => {
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        fixture.detectChanges();
        comp.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledOnce();
        expect(sortServiceSpy).toHaveBeenCalledWith([], 'ID', false);
    });

    it('should call createOptions when performing a search request', fakeAsync(() => {
        // @ts-ignore (the method is protected but exists)
        const createOptionsSpy = jest.spyOn(comp, 'createOptions').mockReturnValue({ test: true });
        setStateAndCallOnInit(() => {
            comp.searchTerm = state.searchTerm = 'newSearchTerm';
            tick(300);
            expect(searchStub).toHaveBeenCalledWith({ ...state }, { test: true });
            expect(createOptionsSpy).toHaveBeenCalled();
        });
    }));

    it('should call onSearchResult after performing a search request', fakeAsync(() => {
        // @ts-ignore (the method is protected but exists)
        const onSearchResultSpy = jest.spyOn(comp, 'onSearchResult');
        setStateAndCallOnInit(() => {
            comp.searchTerm = state.searchTerm = 'newSearchTerm';
            tick(300);
            expect(searchStub).toHaveBeenCalledWith({ ...state }, undefined);
            expect(onSearchResultSpy).toHaveBeenCalled();
        });
    }));
});

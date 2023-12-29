import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { MockComponent, MockDirective } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { CompetencyImportComponent } from 'app/course/competencies/competency-management/competency-import.component';
import { CompetencyPagingService } from 'app/course/competencies/competency-paging.service';
import { Competency } from 'app/entities/competency.model';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';

describe('CompetencyImportComponent', () => {
    let fixture: ComponentFixture<CompetencyImportComponent>;
    let comp: CompetencyImportComponent;
    let pagingService: CompetencyPagingService;
    let sortService: SortService;
    let searchStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let searchResult: SearchResult<Competency>;
    let state: PageableSearch;
    let competency: Competency;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockComponent(NgbPagination)],
            declarations: [CompetencyImportComponent, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyImportComponent);
                comp = fixture.componentInstance;
                pagingService = TestBed.inject(CompetencyPagingService);
                sortService = TestBed.inject(SortService);
                searchStub = jest.spyOn(pagingService, 'search');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(() => {
        fixture.detectChanges();
        competency = new Competency();
        competency.id = 1;
        searchResult = { numberOfPages: 3, resultsOnPage: [competency] };
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

    it('should return competency id', () => {
        expect(comp.trackId(0, competency)).toEqual(competency.id);
    });
});

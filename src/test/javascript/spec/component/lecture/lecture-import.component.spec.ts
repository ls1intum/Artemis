import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { MockComponent, MockDirective } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { Lecture } from 'app/entities/lecture.model';
import { LecturePagingService } from 'app/lecture/lecture-paging.service';
import { LectureImportComponent, TableColumn } from 'app/lecture/lecture-import.component';

describe('LectureImportComponent', () => {
    let fixture: ComponentFixture<LectureImportComponent>;
    let comp: LectureImportComponent;
    let pagingService: LecturePagingService;
    let sortService: SortService;
    let searchForLecturesStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let searchResult: SearchResult<Lecture>;
    let state: PageableSearch;
    let lecture: Lecture;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                LectureImportComponent,
                MockComponent(ButtonComponent),
                MockComponent(NgbHighlight),
                MockComponent(NgbPagination),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureImportComponent);
                comp = fixture.componentInstance;
                pagingService = TestBed.inject(LecturePagingService);
                sortService = TestBed.inject(SortService);
                searchForLecturesStub = jest.spyOn(pagingService, 'searchForLectures');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(() => {
        fixture.detectChanges();
        lecture = new Lecture();
        lecture.id = 1;
        searchResult = { numberOfPages: 3, resultsOnPage: [lecture] };
        state = {
            page: 1,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
            ...searchResult,
        };
        searchForLecturesStub.mockReturnValue(of(searchResult));
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
            expect(searchForLecturesStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING });
            expect(comp.listSorting).toBeTrue();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchForLecturesStub).toHaveBeenCalledWith({ ...state, page: 5 });
            expect(comp.page).toBe(5);
        });
    }));

    it('should set content to paging result on search', fakeAsync(() => {
        expect(comp.searchTerm).toBe('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenSearchTerm';
            comp.searchTerm = givenSearchTerm;
            tick(10);
            expect(searchForLecturesStub).toHaveBeenCalledTimes(0);
            tick(290);
            expect(searchForLecturesStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm });
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toEqual(TableColumn.ID);
        setStateAndCallOnInit(() => {
            comp.sortedColumn = TableColumn.TITLE;
            tick(10);
            expect(searchForLecturesStub).toHaveBeenCalledWith({ ...state, sortedColumn: TableColumn.TITLE });
            expect(comp.sortedColumn).toEqual(TableColumn.TITLE);
        });
    }));

    it('should return lecture id', () => {
        expect(comp.trackId(0, lecture)).toEqual(lecture.id);
    });
});

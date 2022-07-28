import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseImportComponent, TableColumn } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { of } from 'rxjs';
import { FormsModule } from '@angular/forms';

describe('ProgrammingExerciseImportComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseImportComponent>;
    let comp: ProgrammingExerciseImportComponent;
    let pagingService: ProgrammingExercisePagingService;
    let sortService: SortService;
    let searchForExercisesStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let searchResult: SearchResult<ProgrammingExercise>;
    let state: PageableSearch;
    let programmingExercise: ProgrammingExercise;
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ProgrammingExerciseImportComponent,
                MockPipe(ExerciseCourseTitlePipe),
                MockComponent(ButtonComponent),
                MockComponent(NgbHighlight),
                MockComponent(NgbPagination),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseImportComponent);
                comp = fixture.componentInstance;
                pagingService = TestBed.inject(ProgrammingExercisePagingService);
                sortService = TestBed.inject(SortService);
                searchForExercisesStub = jest.spyOn(pagingService, 'searchForExercises');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    beforeEach(() => {
        fixture.detectChanges();
        programmingExercise = { id: 42, title: 'Exercise title', programmingLanguage: ProgrammingLanguage.JAVA } as ProgrammingExercise;
        searchResult = { numberOfPages: 3, resultsOnPage: [programmingExercise] };
        state = {
            page: 1,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
            ...searchResult,
        };
        searchForExercisesStub.mockReturnValue(of(searchResult));
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
            expect(searchForExercisesStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING });
            expect(comp.listSorting).toBeTrue();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchForExercisesStub).toHaveBeenCalledWith({ ...state, page: 5 });
            expect(comp.page).toBe(5);
        });
    }));

    it('should set content to paging result on search', fakeAsync(() => {
        expect(comp.searchTerm).toBe('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenSearchTerm';
            comp.searchTerm = givenSearchTerm;
            tick(10);
            expect(searchForExercisesStub).toHaveBeenCalledTimes(0);
            tick(290);
            expect(searchForExercisesStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm });
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toEqual(TableColumn.ID);
        setStateAndCallOnInit(() => {
            comp.sortedColumn = TableColumn.TITLE;
            tick(10);
            expect(searchForExercisesStub).toHaveBeenCalledWith({ ...state, sortedColumn: TableColumn.TITLE });
            expect(comp.sortedColumn).toEqual(TableColumn.TITLE);
        });
    }));

    it('should return programming exercise id', () => {
        expect(comp.trackId(0, programmingExercise)).toEqual(programmingExercise.id);
    });
});

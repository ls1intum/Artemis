import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseImportComponent, TableColumn } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { ModelingExercisePagingService } from 'app/exercises/modeling/manage/modeling-exercise-paging.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';

describe('ModelingExerciseImportComponent', () => {
    let fixture: ComponentFixture<ModelingExerciseImportComponent>;
    let comp: ModelingExerciseImportComponent;
    let pagingService: ModelingExercisePagingService;
    let sortService: SortService;
    let searchForExercisesStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let searchResult: SearchResult<ModelingExercise>;
    let state: PageableSearch;
    let modelingExercise: ModelingExercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ModelingExerciseImportComponent,
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
                fixture = TestBed.createComponent(ModelingExerciseImportComponent);
                comp = fixture.componentInstance;
                pagingService = TestBed.inject(ModelingExercisePagingService);
                sortService = TestBed.inject(SortService);
                searchForExercisesStub = jest.spyOn(pagingService, 'searchForExercises');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(() => {
        fixture.detectChanges();
        modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        modelingExercise.id = 5;
        searchResult = { numberOfPages: 3, resultsOnPage: [modelingExercise] };
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
            expect(searchForExercisesStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING }, true, true);
            expect(comp.listSorting).toBeTrue();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchForExercisesStub).toHaveBeenCalledWith({ ...state, page: 5 }, true, true);
            expect(comp.page).toBe(5);
        });
    }));

    it('should set content to paging result on search', fakeAsync(() => {
        expect(comp.searchTerm).toBe('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenSearchTerm';
            comp.searchTerm = givenSearchTerm;
            tick(10);
            expect(searchForExercisesStub).not.toHaveBeenCalled();
            tick(290);
            expect(searchForExercisesStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, true, true);
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toEqual(TableColumn.ID);
        setStateAndCallOnInit(() => {
            comp.sortedColumn = TableColumn.TITLE;
            tick(10);
            expect(searchForExercisesStub).toHaveBeenCalledWith({ ...state, sortedColumn: TableColumn.TITLE }, true, true);
            expect(comp.sortedColumn).toEqual(TableColumn.TITLE);
        });
    }));

    it('should return modeling exercise id', () => {
        expect(comp.trackId(0, modelingExercise)).toEqual(modelingExercise.id);
    });

    it('should switch courseFilter/examFilter and search', fakeAsync(() => {
        const pagingServiceSpy = jest.spyOn(pagingService, 'searchForExercises');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<ModelingExercise>));

        fixture.detectChanges();
        expect(comp.isCourseFilter).toBeTrue();
        expect(comp.isExamFilter).toBeTrue();

        comp.onCourseFilterChange();
        comp.onExamFilterChange();
        tick();
        expect(comp.isCourseFilter).toBeFalse();
        expect(comp.isExamFilter).toBeFalse();

        expect(pagingServiceSpy).not.toHaveBeenCalled();
        tick(300);

        const expectedSearchObject = {
            page: 1,
            pageSize: 10,
            searchTerm: '',
            sortedColumn: 'ID',
            sortingOrder: 'DESCENDING',
        };
        expect(pagingServiceSpy).toHaveBeenCalledWith(expectedSearchObject, false, false);
    }));
});

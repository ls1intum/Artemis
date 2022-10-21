import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { PrerequisiteImportComponent, TableColumn } from 'app/course/learning-goals/learning-goal-management/prerequisite-import.component';
import { LearningGoalPagingService } from 'app/course/learning-goals/learning-goal-paging.service';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { MockComponent, MockDirective } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';

describe('PrerequisiteImportComponent', () => {
    let fixture: ComponentFixture<PrerequisiteImportComponent>;
    let comp: PrerequisiteImportComponent;
    let pagingService: LearningGoalPagingService;
    let sortService: SortService;
    let searchForLearningGoalsStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let searchResult: SearchResult<LearningGoal>;
    let state: PageableSearch;
    let learningGoal: LearningGoal;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                PrerequisiteImportComponent,
                MockComponent(ButtonComponent),
                MockComponent(NgbHighlight),
                MockComponent(NgbPagination),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PrerequisiteImportComponent);
                comp = fixture.componentInstance;
                pagingService = TestBed.inject(LearningGoalPagingService);
                sortService = TestBed.inject(SortService);
                searchForLearningGoalsStub = jest.spyOn(pagingService, 'searchForLearningGoals');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(() => {
        fixture.detectChanges();
        learningGoal = new LearningGoal();
        learningGoal.id = 1;
        searchResult = { numberOfPages: 3, resultsOnPage: [learningGoal] };
        state = {
            page: 1,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
            ...searchResult,
        };
        searchForLearningGoalsStub.mockReturnValue(of(searchResult));
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
            expect(searchForLearningGoalsStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING });
            expect(comp.listSorting).toBeTrue();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchForLearningGoalsStub).toHaveBeenCalledWith({ ...state, page: 5 });
            expect(comp.page).toBe(5);
        });
    }));

    it('should set content to paging result on search', fakeAsync(() => {
        expect(comp.searchTerm).toBe('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenSearchTerm';
            comp.searchTerm = givenSearchTerm;
            tick(10);
            expect(searchForLearningGoalsStub).not.toHaveBeenCalled();
            tick(290);
            expect(searchForLearningGoalsStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm });
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toEqual(TableColumn.ID);
        setStateAndCallOnInit(() => {
            comp.sortedColumn = TableColumn.TITLE;
            tick(10);
            expect(searchForLearningGoalsStub).toHaveBeenCalledWith({ ...state, sortedColumn: TableColumn.TITLE });
            expect(comp.sortedColumn).toEqual(TableColumn.TITLE);
        });
    }));

    it('should return learning goal id', () => {
        expect(comp.trackId(0, learningGoal)).toEqual(learningGoal.id);
    });
});

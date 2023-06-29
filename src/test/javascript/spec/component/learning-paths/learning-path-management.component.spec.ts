import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LearningPathManagementComponent, TableColumn } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';
import { LearningPathPagingService } from 'app/course/learning-paths/learning-path-paging.service';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { LearningPath } from 'app/entities/learning-path.model';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/button.component';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

describe('LearningPathManagementComponent', () => {
    let fixture: ComponentFixture<LearningPathManagementComponent>;
    let comp: LearningPathManagementComponent;
    let courseManagementService: CourseManagementService;
    let findCourseStub: jest.SpyInstance;
    let pagingService: LearningPathPagingService;
    let sortService: SortService;
    let searchForLearningPathsStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let searchResult: SearchResult<LearningPath>;
    let state: PageableSearch;
    let learningPath: LearningPath;
    let course: Course;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(NgbPagination)],
            declarations: [LearningPathManagementComponent, MockComponent(ButtonComponent), MockDirective(SortByDirective), MockDirective(SortDirective)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathManagementComponent);
                comp = fixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                findCourseStub = jest.spyOn(courseManagementService, 'find');
                pagingService = TestBed.inject(LearningPathPagingService);
                sortService = TestBed.inject(SortService);
                searchForLearningPathsStub = jest.spyOn(pagingService, 'searchForLearningPaths');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(() => {
        fixture.detectChanges();
        course = new Course();
        course.learningPathEnabled = true;
        findCourseStub.mockReturnValue(of(course));
        learningPath = new LearningPath();
        learningPath.id = 1;
        searchResult = { numberOfPages: 3, resultsOnPage: [learningPath] };
        state = {
            page: 1,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
            ...searchResult,
        };
        searchForLearningPathsStub.mockReturnValue(of(searchResult));
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
            expect(searchForLearningPathsStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING });
            expect(comp.listSorting).toBeTrue();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchForLearningPathsStub).toHaveBeenCalledWith({ ...state, page: 5 });
            expect(comp.page).toBe(5);
        });
    }));

    it('should set content to paging result on search', fakeAsync(() => {
        expect(comp.searchTerm).toBe('');
        setStateAndCallOnInit(() => {
            const givenSearchTerm = 'givenSearchTerm';
            comp.searchTerm = givenSearchTerm;
            tick(10);
            expect(searchForLearningPathsStub).not.toHaveBeenCalled();
            tick(290);
            expect(searchForLearningPathsStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm });
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toEqual(TableColumn.ID);
        setStateAndCallOnInit(() => {
            comp.sortedColumn = TableColumn.USER_LOGIN;
            tick(10);
            expect(searchForLearningPathsStub).toHaveBeenCalledWith({ ...state, sortedColumn: TableColumn.USER_LOGIN });
            expect(comp.sortedColumn).toEqual(TableColumn.USER_LOGIN);
        });
    }));

    it('should return competency id', () => {
        expect(comp.trackId(0, learningPath)).toEqual(learningPath.id);
    });
});

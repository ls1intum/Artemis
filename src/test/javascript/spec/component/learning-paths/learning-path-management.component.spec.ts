import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LearningPathManagementComponent, TableColumn } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';
import { LearningPathPagingService } from 'app/course/learning-paths/learning-path-paging.service';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { LearningPath } from 'app/entities/learning-path.model';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/button.component';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';

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
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: 1,
                            }),
                        },
                    },
                },
                MockProvider(CourseManagementService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathManagementComponent);
                comp = fixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                findCourseStub = jest.spyOn(courseManagementService, 'findWithLearningPaths');
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
        learningPath = new LearningPath();
        learningPath.id = 1;
        course = new Course();
        course.id = 1;
        course.learningPathsEnabled = true;
        course.learningPaths = [learningPath];
        findCourseStub.mockReturnValue(of(new HttpResponse({ body: course })));
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

    it('should load course on init', fakeAsync(() => {
        setStateAndCallOnInit(() => {
            comp.listSorting = true;
            tick(10);
            expect(findCourseStub).toHaveBeenCalledWith(1);
            expect(comp.course).toEqual(course);
        });
    }));

    it('should set content to paging result on sort', fakeAsync(() => {
        expect(comp.listSorting).toBeTrue();
        setStateAndCallOnInit(() => {
            comp.listSorting = false;
            tick(10);
            expect(searchForLearningPathsStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.DESCENDING }, 1);
            expect(comp.listSorting).toBeFalse();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchForLearningPathsStub).toHaveBeenCalledWith({ ...state, page: 5 }, course.id);
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
            expect(searchForLearningPathsStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, course.id);
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toEqual(TableColumn.ID);
        setStateAndCallOnInit(() => {
            comp.sortedColumn = TableColumn.USER_LOGIN;
            tick(10);
            expect(searchForLearningPathsStub).toHaveBeenCalledWith({ ...state, sortedColumn: TableColumn.USER_LOGIN }, course.id);
            expect(comp.sortedColumn).toEqual(TableColumn.USER_LOGIN);
        });
    }));

    it('should return learning path id', () => {
        expect(comp.trackId(0, learningPath)).toEqual(learningPath.id);
    });
});

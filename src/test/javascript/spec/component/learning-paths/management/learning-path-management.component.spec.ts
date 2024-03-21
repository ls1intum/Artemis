import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LearningPathManagementComponent, TableColumn } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';
import { LearningPathPagingService } from 'app/course/learning-paths/learning-path-paging.service';
import { SortService } from 'app/shared/service/sort.service';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { LearningPath } from 'app/entities/competency/learning-path.model';
import { ArtemisTestModule } from '../../../test.module';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/button.component';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { HealthStatus, LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('LearningPathManagementComponent', () => {
    let fixture: ComponentFixture<LearningPathManagementComponent>;
    let comp: LearningPathManagementComponent;
    let alertService: AlertService;
    let alertServiceStub: jest.SpyInstance;
    let pagingService: LearningPathPagingService;
    let sortService: SortService;
    let searchStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let searchResult: SearchResult<LearningPath>;
    let state: SearchTermPageableSearch;
    let learningPath: LearningPath;
    let learningPathService: LearningPathService;
    let enableLearningPathsStub: jest.SpyInstance;
    let generateMissingLearningPathsForCourseStub: jest.SpyInstance;
    let getHealthStatusForCourseStub: jest.SpyInstance;
    let health: LearningPathHealthDTO;
    let courseId: number;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockComponent(NgbPagination)],
            declarations: [
                LearningPathManagementComponent,
                MockComponent(ButtonComponent),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathManagementComponent);
                comp = fixture.componentInstance;
                alertService = TestBed.inject(AlertService);
                alertServiceStub = jest.spyOn(alertService, 'error');
                pagingService = TestBed.inject(LearningPathPagingService);
                sortService = TestBed.inject(SortService);
                searchStub = jest.spyOn(pagingService, 'search');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
                learningPathService = TestBed.inject(LearningPathService);
                enableLearningPathsStub = jest.spyOn(learningPathService, 'enableLearningPaths');
                generateMissingLearningPathsForCourseStub = jest.spyOn(learningPathService, 'generateMissingLearningPathsForCourse');
                getHealthStatusForCourseStub = jest.spyOn(learningPathService, 'getHealthStatusForCourse');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(() => {
        fixture.detectChanges();
        learningPath = new LearningPath();
        learningPath.id = 2;
        courseId = 1;
        searchResult = { numberOfPages: 3, resultsOnPage: [learningPath] };
        state = {
            page: 1,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
            ...searchResult,
        };
        searchStub.mockReturnValue(of(searchResult));
        enableLearningPathsStub.mockReturnValue(of(new HttpResponse<void>()));
        generateMissingLearningPathsForCourseStub.mockReturnValue(of(new HttpResponse<void>()));
        health = new LearningPathHealthDTO([HealthStatus.OK]);
        getHealthStatusForCourseStub.mockReturnValue(of(new HttpResponse({ body: health })));
    });

    const setStateAndCallOnInit = (middleExpectation: () => void) => {
        comp.state = { ...state };
        comp.ngOnInit();
        middleExpectation();
        expect(comp.content).toEqual(searchResult);
        comp.sortRows();
        expect(sortByPropertyStub).toHaveBeenCalledWith(searchResult.resultsOnPage, comp.sortedColumn, comp.listSorting);
    };

    it('should load health status on init', fakeAsync(() => {
        setStateAndCallOnInit(() => {
            comp.listSorting = true;
            tick(10);
            expect(getHealthStatusForCourseStub).toHaveBeenCalledWith(courseId);
            expect(comp.health).toEqual(health);
        });
    }));

    it('should alert error if loading health status fails', fakeAsync(() => {
        const error = { status: 404 };
        getHealthStatusForCourseStub.mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        fixture.detectChanges();
        comp.ngOnInit();
        expect(getHealthStatusForCourseStub).toHaveBeenCalledWith(courseId);
        expect(alertServiceStub).toHaveBeenCalledOnce();
    }));

    it('should enable learning paths and load data', fakeAsync(() => {
        const healthDisabled = new LearningPathHealthDTO([HealthStatus.DISABLED]);
        getHealthStatusForCourseStub.mockReturnValueOnce(of(new HttpResponse({ body: healthDisabled }))).mockReturnValueOnce(of(new HttpResponse({ body: health })));
        fixture.detectChanges();
        comp.ngOnInit();
        expect(comp.health).toEqual(healthDisabled);
        comp.enableLearningPaths();
        expect(enableLearningPathsStub).toHaveBeenCalledOnce();
        expect(enableLearningPathsStub).toHaveBeenCalledWith(courseId);
        expect(getHealthStatusForCourseStub).toHaveBeenCalledTimes(3);
        expect(comp.health).toEqual(health);
    }));

    it('should alert error if enable learning paths fails', fakeAsync(() => {
        const error = { status: 404 };
        enableLearningPathsStub.mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        fixture.detectChanges();
        comp.ngOnInit();
        comp.enableLearningPaths();
        expect(enableLearningPathsStub).toHaveBeenCalledWith(courseId);
        expect(alertServiceStub).toHaveBeenCalledOnce();
    }));

    it('should generate missing learning paths and load data', fakeAsync(() => {
        const healthMissing = new LearningPathHealthDTO([HealthStatus.MISSING]);
        getHealthStatusForCourseStub.mockReturnValueOnce(of(new HttpResponse({ body: healthMissing }))).mockReturnValueOnce(of(new HttpResponse({ body: health })));
        fixture.detectChanges();
        comp.ngOnInit();
        expect(comp.health).toEqual(healthMissing);
        comp.generateMissing();
        expect(generateMissingLearningPathsForCourseStub).toHaveBeenCalledOnce();
        expect(generateMissingLearningPathsForCourseStub).toHaveBeenCalledWith(courseId);
        expect(getHealthStatusForCourseStub).toHaveBeenCalledTimes(3);
        expect(comp.health).toEqual(health);
    }));

    it('should alert error if generate missing learning paths fails', fakeAsync(() => {
        const error = { status: 404 };
        generateMissingLearningPathsForCourseStub.mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        fixture.detectChanges();
        comp.ngOnInit();
        comp.generateMissing();
        expect(generateMissingLearningPathsForCourseStub).toHaveBeenCalledWith(courseId);
        expect(alertServiceStub).toHaveBeenCalledOnce();
    }));

    it('should set content to paging result on sort', fakeAsync(() => {
        expect(comp.listSorting).toBeTrue();
        setStateAndCallOnInit(() => {
            comp.listSorting = false;
            tick(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.DESCENDING }, { courseId });
            expect(comp.listSorting).toBeFalse();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, page: 5 }, { courseId });
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
            expect(searchStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, { courseId });
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toEqual(TableColumn.ID);
        setStateAndCallOnInit(() => {
            comp.sortedColumn = TableColumn.USER_LOGIN;
            tick(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, sortedColumn: TableColumn.USER_LOGIN }, { courseId });
            expect(comp.sortedColumn).toEqual(TableColumn.USER_LOGIN);
        });
    }));

    it('should return learning path id', () => {
        expect(comp.trackId(0, learningPath)).toEqual(learningPath.id);
    });
});

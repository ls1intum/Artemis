import { Injector } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ModelingExercisePagingService } from 'app/exercises/modeling/manage/modeling-exercise-paging.service';
import { CodeAnalysisPagingService } from 'app/exercises/programming/manage/services/code-analysis-paging.service';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { QuizExercisePagingService } from 'app/exercises/quiz/manage/quiz-exercise-paging.service';
import { ExerciseImportComponent } from 'app/exercises/shared/import/exercise-import.component';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { TextExercisePagingService } from 'app/exercises/text/manage/text-exercise/text-exercise-paging.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { FileUploadExercisePagingService } from 'app/exercises/file-upload/manage/file-upload-exercise-paging.service';

describe('ExerciseImportComponent', () => {
    let fixture: ComponentFixture<ExerciseImportComponent>;
    let comp: ExerciseImportComponent;

    let quizExercisePagingService: QuizExercisePagingService;
    let sortService: SortService;
    let injector: Injector;
    let activeModal: NgbActiveModal;
    let searchStub: jest.SpyInstance;
    let sortByPropertyStub: jest.SpyInstance;
    let searchResult: SearchResult<Exercise>;
    let state: SearchTermPageableSearch;
    let quizExercise: QuizExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockComponent(NgbPagination)],
            declarations: [
                ExerciseImportComponent,
                MockPipe(ExerciseCourseTitlePipe),
                MockComponent(ButtonComponent),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockDirective(TranslateDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseImportComponent);
                comp = fixture.componentInstance;
                quizExercisePagingService = TestBed.get(QuizExercisePagingService);
                sortService = TestBed.inject(SortService);
                injector = TestBed.inject(Injector);
                activeModal = TestBed.inject(NgbActiveModal);
                searchStub = jest.spyOn(quizExercisePagingService, 'search');
                sortByPropertyStub = jest.spyOn(sortService, 'sortByProperty');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(() => {
        comp.exerciseType = ExerciseType.QUIZ;
        quizExercise = new QuizExercise(undefined, undefined);
        quizExercise.id = 5;
        searchResult = { numberOfPages: 3, resultsOnPage: [quizExercise] };
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
        // WHEN
        fixture.detectChanges();

        // THEN
        expect(comp.content).toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });

    it('should close the active modal', () => {
        const dismiss = jest.spyOn(activeModal, 'dismiss');

        // WHEN
        comp.clear();

        // THEN
        expect(dismiss).toHaveBeenCalledExactlyOnceWith('cancel');
    });

    it('should close the active modal with result', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'close');
        const exercise = { id: 1 } as TextExercise;
        // WHEN
        comp.selectImport(exercise);

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith(exercise);
    });

    it('should change the page on active modal', fakeAsync(() => {
        const defaultPageSize = 10;
        const numberOfPages = 5;
        const pagingServiceSpy = jest.spyOn(quizExercisePagingService, 'search');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages } as SearchResult<TextExercise>));

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

    it('should set search term and search', fakeAsync(() => {
        searchStub.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<TextExercise>));

        fixture.detectChanges();

        const expectedSearchTerm = 'search term';
        comp.searchTerm = expectedSearchTerm;
        tick();
        expect(comp.searchTerm).toBe(expectedSearchTerm);

        // It should wait first before executing search.
        expect(searchStub).not.toHaveBeenCalled();

        tick(300);

        expect(searchStub).toHaveBeenCalledOnce();
    }));

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
            expect(searchStub).toHaveBeenCalledWith({ ...state, sortingOrder: SortingOrder.ASCENDING }, { isCourseFilter: true, isExamFilter: true });
            expect(comp.listSorting).toBeTrue();
        });
    }));

    it('should set content to paging result on pageChange', fakeAsync(() => {
        expect(comp.page).toBe(1);
        setStateAndCallOnInit(() => {
            comp.onPageChange(5);
            tick(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, page: 5 }, { isCourseFilter: true, isExamFilter: true });
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
            expect(searchStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, { isCourseFilter: true, isExamFilter: true });
            expect(comp.searchTerm).toEqual(givenSearchTerm);
        });
    }));

    it('should set content to paging result on sortedColumn change', fakeAsync(() => {
        expect(comp.sortedColumn).toBe('ID');
        setStateAndCallOnInit(() => {
            comp.sortedColumn = 'TITLE';
            tick(10);
            expect(searchStub).toHaveBeenCalledWith({ ...state, sortedColumn: 'TITLE' }, { isCourseFilter: true, isExamFilter: true });
            expect(comp.sortedColumn).toBe('TITLE');
        });
    }));

    it('should return quiz exercise id', () => {
        expect(comp.trackId(0, quizExercise)).toEqual(quizExercise.id);
    });

    it('should switch courseFilter/examFilter and search', fakeAsync(() => {
        searchStub.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<QuizExercise>));

        fixture.detectChanges();
        expect(comp.isCourseFilter).toBeTrue();
        expect(comp.isExamFilter).toBeTrue();

        comp.onCourseFilterChange();
        comp.onExamFilterChange();
        tick();
        expect(comp.isCourseFilter).toBeFalse();
        expect(comp.isExamFilter).toBeFalse();

        expect(searchStub).not.toHaveBeenCalled();
        tick(300);

        const expectedSearchObject = {
            page: 1,
            pageSize: 10,
            searchTerm: '',
            sortedColumn: 'ID',
            sortingOrder: 'DESCENDING',
        };
        expect(searchStub).toHaveBeenCalledWith(expectedSearchObject, { isCourseFilter: false, isExamFilter: false });
    }));

    it.each([
        [ExerciseType.PROGRAMMING, ProgrammingExercisePagingService],
        [ExerciseType.TEXT, TextExercisePagingService],
        [ExerciseType.MODELING, ModelingExercisePagingService],
        [ExerciseType.QUIZ, QuizExercisePagingService],
        [ExerciseType.FILE_UPLOAD, FileUploadExercisePagingService],
    ])(
        'uses the correct paging service',
        fakeAsync((exerciseType: ExerciseType, expectedPagingService: typeof PagingService) => {
            const getSpy = jest.spyOn(injector, 'get');
            // This is needed for `.toHaveBeenCalledWith` to work properly:
            getSpy.mockImplementation(() => undefined);

            comp.exerciseType = exerciseType;

            comp.ngOnInit();

            expect(getSpy).toHaveBeenCalledExactlyOnceWith(expectedPagingService, undefined, 0); // default values for arguments 2 and 3
        }),
    );

    it('should allow importing SCA configurations', () => {
        const getSpy = jest.spyOn(injector, 'get');
        // This is needed for `.toHaveBeenCalledWith` to work properly:
        getSpy.mockImplementation(() => undefined);

        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.programmingLanguage = ProgrammingLanguage.JAVA;

        comp.ngOnInit();

        expect(comp.titleKey).toContain('configureGrading');
        expect(getSpy).toHaveBeenCalledExactlyOnceWith(CodeAnalysisPagingService, undefined, 0);
    });

    it('should sort by exam title when only the exam filter is active', () => {
        comp.isExamFilter = true;
        comp.isCourseFilter = false;

        comp.sortedColumn = 'COURSE_TITLE';

        expect(comp.sortedColumn).toBe('EXAM_TITLE');
    });
});

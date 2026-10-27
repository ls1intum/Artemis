import { Injector } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject, of, throwError } from 'rxjs';
import { MockComponent } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { FileUploadExercisePagingService } from 'app/fileupload/manage/services/file-upload-exercise-paging.service';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercisePagingService } from 'app/modeling/manage/services/modeling-exercise-paging.service';
import { CodeAnalysisPagingService } from 'app/programming/manage/services/code-analysis-paging.service';
import { ProgrammingExercisePagingService } from 'app/programming/manage/services/programming-exercise-paging.service';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { QuizExercisePagingService } from 'app/quiz/manage/service/quiz-exercise-paging.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { SortService } from 'app/foundation/service/sort.service';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TextExercisePagingService } from 'app/text/manage/text-exercise/service/text-exercise-paging.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizQuestionPreviewComponent } from 'app/quiz/manage/quiz-question-preview/quiz-question-preview.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AlertService } from 'app/foundation/service/alert.service';

describe('ExerciseImportComponent', () => {
    let fixture: ComponentFixture<ExerciseImportComponent>;
    let comp: ExerciseImportComponent;

    let quizExercisePagingService: QuizExercisePagingService;
    let sortService: SortService;
    let injector: Injector;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;
    let searchStub: ReturnType<typeof vi.spyOn>;
    let sortByPropertyStub: ReturnType<typeof vi.spyOn>;
    let searchResult: SearchResult<Exercise>;
    let state: SearchTermPageableSearch;
    let quizExercise: QuizExercise;

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<Exercise | undefined>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(ExerciseImportComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseImportComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseType', ExerciseType.QUIZ);
        quizExercisePagingService = TestBed.inject(QuizExercisePagingService);
        sortService = TestBed.inject(SortService);
        injector = TestBed.inject(Injector);
        searchStub = vi.spyOn(quizExercisePagingService, 'search');
        sortByPropertyStub = vi.spyOn(sortService, 'sortByProperty');

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

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllTimers();
        vi.useRealTimers();
    });

    it('should initialize the content', () => {
        fixture.detectChanges();

        expect(comp.content()).toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });

    it('should close the dialog', () => {
        comp.clear();

        expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith();
    });

    it('should close the dialog with result', () => {
        const exercise = { id: 1 } as TextExercise;

        comp.selectImport(exercise);

        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith(exercise);
    });

    it('should change the page on active modal', () => {
        vi.useFakeTimers();
        const defaultPageSize = 10;
        const numberOfPages = 5;
        const pagingServiceSpy = vi.spyOn(quizExercisePagingService, 'search');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages } as SearchResult<TextExercise>));

        fixture.detectChanges();

        let expectedPageNumber = 1;
        comp.onPageChange(expectedPageNumber);
        vi.advanceTimersByTime(10);
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total()).toBe(numberOfPages * defaultPageSize);

        expectedPageNumber = 2;
        comp.onPageChange(expectedPageNumber);
        vi.advanceTimersByTime(10);
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total()).toBe(numberOfPages * defaultPageSize);

        comp.onPageChange(0);
        vi.advanceTimersByTime(10);
        expect(comp.page).toBe(expectedPageNumber);

        expect(pagingServiceSpy).toHaveBeenCalledTimes(2);
    });

    it('should sort rows with default values', () => {
        const sortServiceSpy = vi.spyOn(sortService, 'sortByProperty');

        fixture.detectChanges();
        comp.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledOnce();
        expect(sortServiceSpy).toHaveBeenCalledWith([], 'ID', false);
    });

    it('should set search term and search', () => {
        vi.useFakeTimers();
        searchStub.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<TextExercise>));

        fixture.detectChanges();
        // Flush and ignore the initial load performed on init (see the ImportComponent regression test),
        // then assert the debounced search behavior triggered by the search term.
        vi.advanceTimersByTime(300);
        searchStub.mockClear();

        const expectedSearchTerm = 'search term';
        comp.searchTerm = expectedSearchTerm;
        vi.advanceTimersByTime(299);
        expect(comp.searchTerm).toBe(expectedSearchTerm);
        expect(searchStub).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1);

        expect(searchStub).toHaveBeenCalledOnce();
    });

    const resetContent = () => {
        comp.content.set({ resultsOnPage: [], numberOfPages: 0 });
    };

    // The paging service is mocked to emit synchronously via of(...), so the only asynchrony is the
    // debounceTime in performSearch. We therefore drive the virtual clock with the synchronous
    // vi.advanceTimersByTime(...) and keep these tests synchronous. Mixing in await /
    // advanceTimersByTimeAsync(...) interleaves the microtask queue between the two parallel
    // (sort debounce 0 / search debounce 300) subscriptions, which made these tests flaky.
    const initializeSearchSubscriptions = () => {
        comp.state = { ...state };
        comp.ngOnInit();
        vi.advanceTimersByTime(300);
        expect(searchStub).toHaveBeenCalledWith(state, { isCourseFilter: true, isExamFilter: true, programmingLanguage: undefined });
        expect(comp.content()).toEqual(searchResult);
        searchStub.mockClear();
        resetContent();
    };

    const expectSearchResultAfterAction = (action: () => void, expectedState: SearchTermPageableSearch) => {
        initializeSearchSubscriptions();

        action();
        vi.advanceTimersByTime(10);

        expect(searchStub).toHaveBeenCalledWith(expectedState, { isCourseFilter: true, isExamFilter: true, programmingLanguage: undefined });
        expect(comp.content()).toEqual(searchResult);
        comp.sortRows();
        expect(sortByPropertyStub).toHaveBeenCalledWith(searchResult.resultsOnPage, comp.sortedColumn, comp.listSorting);
    };

    it('should set content to paging result on sort', () => {
        vi.useFakeTimers();
        expect(comp.listSorting).toBe(false);

        expectSearchResultAfterAction(
            () => {
                comp.listSorting = true;
            },
            { ...state, sortingOrder: SortingOrder.ASCENDING },
        );

        expect(comp.listSorting).toBe(true);
    });

    it('should set content to paging result on pageChange', () => {
        vi.useFakeTimers();
        expect(comp.page).toBe(1);

        expectSearchResultAfterAction(
            () => {
                comp.onPageChange(5);
            },
            { ...state, page: 5 },
        );

        expect(comp.page).toBe(5);
    });

    it('should set content to paging result on search', () => {
        vi.useFakeTimers();
        expect(comp.searchTerm).toBe('');
        initializeSearchSubscriptions();

        const givenSearchTerm = 'givenSearchTerm';
        comp.searchTerm = givenSearchTerm;
        vi.advanceTimersByTime(10);
        expect(searchStub).not.toHaveBeenCalled();
        vi.advanceTimersByTime(290);

        expect(searchStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, { isCourseFilter: true, isExamFilter: true, programmingLanguage: undefined });

        expect(comp.searchTerm).toEqual(givenSearchTerm);
        expect(comp.content()).toEqual(searchResult);
        comp.sortRows();
        expect(sortByPropertyStub).toHaveBeenCalledWith(searchResult.resultsOnPage, comp.sortedColumn, comp.listSorting);
    });

    it('should set content to paging result on sortedColumn change', () => {
        vi.useFakeTimers();
        expect(comp.sortedColumn).toBe('ID');

        expectSearchResultAfterAction(
            () => {
                comp.sortedColumn = 'TITLE';
            },
            { ...state, sortedColumn: 'TITLE' },
        );

        expect(comp.sortedColumn).toBe('TITLE');
    });

    it('should return quiz exercise id', () => {
        expect(comp.trackId(0, quizExercise)).toEqual(quizExercise.id);
    });

    it('should switch courseFilter/examFilter and search', () => {
        vi.useFakeTimers();
        searchStub.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<QuizExercise>));

        fixture.detectChanges();
        expect(comp.isCourseFilter()).toBe(true);
        expect(comp.isExamFilter()).toBe(true);
        // Flush and ignore the initial load performed on init before asserting the filter-triggered search.
        vi.advanceTimersByTime(300);
        searchStub.mockClear();

        comp.onCourseFilterChange();
        comp.onExamFilterChange();
        vi.advanceTimersByTime(299);
        expect(comp.isCourseFilter()).toBe(false);
        expect(comp.isExamFilter()).toBe(false);
        expect(searchStub).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1);

        const expectedSearchObject = {
            page: 1,
            pageSize: 10,
            searchTerm: '',
            sortedColumn: 'ID',
            sortingOrder: 'DESCENDING',
        };
        expect(searchStub).toHaveBeenCalledWith(expectedSearchObject, { isCourseFilter: false, isExamFilter: false, programmingLanguage: undefined });
    });

    it.each([
        [ExerciseType.PROGRAMMING, ProgrammingExercisePagingService],
        [ExerciseType.TEXT, TextExercisePagingService],
        [ExerciseType.MODELING, ModelingExercisePagingService],
        [ExerciseType.QUIZ, QuizExercisePagingService],
        [ExerciseType.FILE_UPLOAD, FileUploadExercisePagingService],
    ])('uses the correct paging service', (exerciseType: ExerciseType, expectedPagingService: any) => {
        vi.useFakeTimers();
        const pagingServiceMock = {
            search: vi.fn().mockReturnValue(of({ numberOfPages: 0, resultsOnPage: [] })),
        };
        const getSpy = vi.spyOn(injector, 'get').mockReturnValue(pagingServiceMock as any);
        fixture.componentRef.setInput('exerciseType', exerciseType);

        comp.ngOnInit();
        vi.advanceTimersByTime(300);

        expect(getSpy).toHaveBeenCalledWith(expectedPagingService, {});
        expect(pagingServiceMock.search).toHaveBeenCalled();
    });

    it('should allow importing SCA configurations', () => {
        vi.useFakeTimers();
        const pagingServiceMock = {
            search: vi.fn().mockReturnValue(of({ numberOfPages: 0, resultsOnPage: [] })),
        };
        const getSpy = vi.spyOn(injector, 'get').mockReturnValue(pagingServiceMock as any);
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        fixture.componentRef.setInput('programmingLanguage', ProgrammingLanguage.JAVA);

        comp.ngOnInit();
        vi.advanceTimersByTime(300);

        expect(comp.titleKey).toContain('configureGrading');
        expect(getSpy).toHaveBeenCalledWith(CodeAnalysisPagingService, {});
        expect(pagingServiceMock.search).toHaveBeenCalled();
    });

    it('should sort by exam title when only the exam filter is active', () => {
        comp.isExamFilter.set(true);
        comp.isCourseFilter.set(false);

        comp.sortedColumn = 'COURSE_TITLE';

        expect(comp.sortedColumn).toBe('EXAM_TITLE');
    });

    describe('quiz preview', () => {
        let findStub: ReturnType<typeof vi.spyOn>;
        let alertErrorStub: ReturnType<typeof vi.spyOn>;
        let loadedQuiz: QuizExercise;

        beforeEach(() => {
            loadedQuiz = new QuizExercise(undefined, undefined);
            loadedQuiz.id = 5;
            loadedQuiz.title = 'Loaded quiz';
            findStub = vi.spyOn(TestBed.inject(QuizExerciseService), 'find').mockReturnValue(of(loadedQuiz));
            alertErrorStub = vi.spyOn(TestBed.inject(AlertService), 'error');
        });

        it('should load the complete quiz of the row and show it', () => {
            comp.openPreview(quizExercise);

            expect(findStub).toHaveBeenCalledExactlyOnceWith(5);
            expect(comp.preview()).toEqual({ exercise: quizExercise, quiz: loadedQuiz });
            expect(comp.previewVisible()).toBe(true);
            expect(comp.previewLoadingId()).toBeUndefined();
        });

        it('should mark the row as loading until the quiz arrived', () => {
            const response = new Subject<QuizExercise>();
            findStub.mockReturnValue(response);

            comp.openPreview(quizExercise);

            expect(comp.previewLoadingId()).toBe(5);
            expect(comp.previewVisible()).toBe(false);

            response.next(loadedQuiz);

            expect(comp.previewLoadingId()).toBeUndefined();
            expect(comp.previewVisible()).toBe(true);
        });

        it('should load only one quiz at a time', () => {
            findStub.mockReturnValue(new Subject<QuizExercise>());
            const otherExercise = new QuizExercise(undefined, undefined);
            otherExercise.id = 6;

            comp.openPreview(quizExercise);
            comp.openPreview(otherExercise);

            expect(findStub).toHaveBeenCalledExactlyOnceWith(5);
        });

        it('should not load anything for a row without id', () => {
            comp.openPreview(new QuizExercise(undefined, undefined));

            expect(findStub).not.toHaveBeenCalled();
            expect(comp.previewVisible()).toBe(false);
        });

        it('should alert and stay on the import dialog when loading fails', () => {
            findStub.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            comp.openPreview(quizExercise);

            expect(alertErrorStub).toHaveBeenCalledExactlyOnceWith('artemisApp.exercise.import.preview.loadFailed');
            expect(comp.previewVisible()).toBe(false);
            expect(comp.preview()).toBeUndefined();
            expect(comp.previewLoadingId()).toBeUndefined();
            expect(dialogRefCloseSpy).not.toHaveBeenCalled();
        });

        it('should import the row of the previewed quiz and close the preview', () => {
            const selectedStub = vi.fn();
            comp.selected.subscribe(selectedStub);
            comp.openPreview(quizExercise);

            comp.importPreviewed();

            expect(comp.previewVisible()).toBe(false);
            expect(selectedStub).toHaveBeenCalledExactlyOnceWith(quizExercise);
            expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith(quizExercise);
        });

        it('should not import anything without a previewed quiz', () => {
            comp.importPreviewed();

            expect(dialogRefCloseSpy).not.toHaveBeenCalled();
        });
    });
});

describe('ExerciseImportComponent template', () => {
    let fixture: ComponentFixture<ExerciseImportComponent>;
    let comp: ExerciseImportComponent;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        await TestBed.configureTestingModule({
            providers: [
                { provide: DynamicDialogRef, useValue: { close: dialogRefCloseSpy, onClose: new Subject<Exercise | undefined>() } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            // Render the real template but stub the translate directive so no TranslateService is required.
            // The rendering of the questions is covered by the spec of the preview component.
            .overrideComponent(ExerciseImportComponent, {
                remove: { imports: [TranslateDirective, QuizQuestionPreviewComponent] },
                add: { imports: [MockJhiTranslateDirective, MockComponent(QuizQuestionPreviewComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseImportComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseType', ExerciseType.QUIZ);
        vi.spyOn(TestBed.inject(QuizExercisePagingService), 'search').mockReturnValue(of({ numberOfPages: 0, resultsOnPage: [] }));
    });

    it('should render the results as a normal table with the header aligned above the rows', () => {
        const exercise = new QuizExercise(undefined, undefined);
        exercise.id = 1;
        exercise.title = 'Some exercise';

        // detect changes first so that ngOnInit runs (it resets the content), then populate the table
        fixture.detectChanges();
        comp.content.set({ resultsOnPage: [exercise], numberOfPages: 1 });
        fixture.detectChanges();

        const table: HTMLTableElement = fixture.nativeElement.querySelector('table');
        // Regression guard: a bare `flex` class turned the table into a flex row, so the browser
        // laid out <thead> and <tbody> as side-by-side items (header next to the rows) instead of
        // stacking them. The table (and its rows) must not carry the flex classes.
        expect(table.classList.contains('flex')).toBe(false);
        expect(table.querySelector('thead tr')?.classList.contains('flex-row')).toBe(false);
        expect(table.querySelector('tbody tr')?.classList.contains('flex-row')).toBe(false);

        // In a normal table every row has the same number of columns as the header, so they align.
        const headerColumns = table.querySelectorAll('thead tr th').length;
        const firstRowColumns = table.querySelectorAll('tbody tr:first-child td').length;
        expect(firstRowColumns).toBe(headerColumns);
    });

    describe('quiz preview', () => {
        let findStub: ReturnType<typeof vi.spyOn>;
        let quiz: QuizExercise;
        let row: QuizExercise;

        const overlayElement = (testId: string): HTMLElement | null => document.body.querySelector(`[data-testid="${testId}"]`);

        const showRows = (rows: Exercise[]) => {
            // detect changes first so that ngOnInit runs (it resets the content), then populate the table
            fixture.detectChanges();
            comp.content.set({ resultsOnPage: rows, numberOfPages: 1 });
            fixture.detectChanges();
        };

        const clickPreview = (exerciseId: number) => {
            (fixture.nativeElement.querySelector(`[data-testid="preview-exercise-${exerciseId}"] button`) as HTMLButtonElement).click();
            fixture.detectChanges();
        };

        beforeEach(() => {
            row = new QuizExercise(undefined, undefined);
            row.id = 1;
            row.title = 'Some quiz';

            const first = new MultipleChoiceQuestion();
            first.id = 11;
            first.points = 3;
            const second = new MultipleChoiceQuestion();
            second.id = 12;
            second.points = 4;
            quiz = new QuizExercise(undefined, undefined);
            quiz.id = 1;
            quiz.title = 'Some quiz';
            quiz.duration = 5400;
            quiz.quizQuestions = [first, second];
            findStub = vi.spyOn(TestBed.inject(QuizExerciseService), 'find').mockReturnValue(of(quiz));
        });

        afterEach(() => {
            fixture.destroy();
        });

        it('should offer a preview button next to the import button of a quiz row', () => {
            showRows([row]);

            const cell = fixture.nativeElement.querySelector('tbody tr td:last-child') as HTMLElement;
            expect(cell.querySelector('[data-testid="preview-exercise-1"]')).not.toBeNull();
            expect(cell.querySelector('.import')).not.toBeNull();
        });

        it.each([
            ['text', ExerciseType.TEXT, TextExercisePagingService, undefined],
            ['modeling', ExerciseType.MODELING, ModelingExercisePagingService, undefined],
            ['programming', ExerciseType.PROGRAMMING, ProgrammingExercisePagingService, undefined],
            ['SCA configuration of a programming', ExerciseType.PROGRAMMING, CodeAnalysisPagingService, ProgrammingLanguage.JAVA],
        ])('should not offer a preview for %s exercises', (_name, exerciseType, pagingServiceType, programmingLanguage) => {
            const otherRow = { id: 2, title: 'Other exercise', type: exerciseType } as Exercise;
            vi.spyOn(TestBed.inject(pagingServiceType as typeof TextExercisePagingService), 'search').mockReturnValue(of({ numberOfPages: 1, resultsOnPage: [otherRow] }));
            fixture.componentRef.setInput('exerciseType', exerciseType);
            fixture.componentRef.setInput('programmingLanguage', programmingLanguage);

            showRows([otherRow]);

            expect(fixture.nativeElement.querySelector('tbody tr .import')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid^="preview-exercise-"]')).toBeNull();
        });

        it('should load the quiz and open the preview dialog with its summary and questions', () => {
            showRows([row]);
            expect(overlayElement('exercise-import-preview')).toBeNull();

            clickPreview(1);

            expect(findStub).toHaveBeenCalledExactlyOnceWith(1);
            expect(overlayElement('exercise-import-preview')).not.toBeNull();
            expect(overlayElement('exercise-import-preview-points')!.textContent).toBe('7');
            expect(overlayElement('exercise-import-preview-duration')!.textContent).toBe('1h 30min');
            expect(document.body.querySelectorAll('jhi-quiz-question-preview')).toHaveLength(2);
            expect(document.body.querySelector('.tumaet-ui-dialog-title')!.textContent).toContain('artemisApp.exercise.import.preview.title');
        });

        it('should say so when the previewed quiz has no questions', () => {
            quiz.quizQuestions = [];
            showRows([row]);

            clickPreview(1);

            expect(overlayElement('exercise-import-preview-empty')).not.toBeNull();
            expect(document.body.querySelector('jhi-quiz-question-preview')).toBeNull();
        });

        it('should import the exercise from the preview', () => {
            showRows([row]);
            clickPreview(1);

            overlayElement('exercise-import-preview-import')!.querySelector('button')!.click();
            fixture.detectChanges();

            expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith(row);
            expect(overlayElement('exercise-import-preview')).toBeNull();
        });

        it('should close only the preview and keep the import dialog when the close button is pressed', () => {
            showRows([row]);
            clickPreview(1);

            overlayElement('exercise-import-preview-close')!.querySelector('button')!.click();
            fixture.detectChanges();

            expect(overlayElement('exercise-import-preview')).toBeNull();
            expect(dialogRefCloseSpy).not.toHaveBeenCalled();
        });

        it('should give the focus back to the preview button of the row when the preview is closed', () => {
            showRows([row]);
            clickPreview(1);
            expect(overlayElement('exercise-import-preview')).not.toBeNull();

            overlayElement('exercise-import-preview-close')!.querySelector('button')!.click();
            fixture.detectChanges();

            expect(overlayElement('exercise-import-preview')).toBeNull();
            expect(document.activeElement).toBe(fixture.nativeElement.querySelector('[data-testid="preview-exercise-1"] button'));
        });

        it('should label the preview button of the row with the title of the quiz', () => {
            showRows([row]);

            const button = fixture.nativeElement.querySelector('[data-testid="preview-exercise-1"] button') as HTMLButtonElement;
            expect(button.getAttribute('aria-label')).toBe('artemisApp.exercise.import.preview.buttonLabel');
        });

        it('should not alert about a failed preview that was requested by a component that is gone', () => {
            const response = new Subject<QuizExercise>();
            findStub.mockReturnValue(response);
            const alertErrorStub = vi.spyOn(TestBed.inject(AlertService), 'error');
            showRows([row]);
            clickPreview(1);

            fixture.destroy();
            response.error(new HttpErrorResponse({ status: 500 }));

            expect(alertErrorStub).not.toHaveBeenCalled();
        });

        it('should stop handling Escape for the preview when the component is destroyed while the preview is open', () => {
            const outerEscapeListener = vi.fn();
            document.addEventListener('keydown', outerEscapeListener);
            try {
                showRows([row]);
                clickPreview(1);
                fixture.destroy();

                document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

                expect(outerEscapeListener).toHaveBeenCalledOnce();
            } finally {
                document.removeEventListener('keydown', outerEscapeListener);
            }
        });

        it('should not let Escape reach the import dialog while the preview is open', () => {
            // The PrimeNG dialog hosting the import closes on a document keydown listener, which this one stands in for.
            const importDialogEscapeListener = vi.fn();
            document.addEventListener('keydown', importDialogEscapeListener);
            try {
                showRows([row]);
                clickPreview(1);
                expect(comp.previewVisible()).toBe(true);

                document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
                fixture.detectChanges();

                expect(importDialogEscapeListener).not.toHaveBeenCalled();
                expect(comp.previewVisible()).toBe(false);
                expect(overlayElement('exercise-import-preview')).toBeNull();

                // With the preview closed, Escape reaches the import dialog again.
                document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
                expect(importDialogEscapeListener).toHaveBeenCalledOnce();
            } finally {
                document.removeEventListener('keydown', importDialogEscapeListener);
            }
        });
    });
});

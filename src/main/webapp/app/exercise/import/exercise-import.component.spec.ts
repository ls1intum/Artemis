import { Injector } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { FileUploadExercisePagingService } from 'app/fileupload/manage/services/file-upload-exercise-paging.service';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercisePagingService } from 'app/modeling/manage/services/modeling-exercise-paging.service';
import { CodeAnalysisPagingService } from 'app/programming/manage/services/code-analysis-paging.service';
import { ProgrammingExercisePagingService } from 'app/programming/manage/services/programming-exercise-paging.service';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { QuizExercisePagingService } from 'app/quiz/manage/service/quiz-exercise-paging.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { SortService } from 'app/shared/service/sort.service';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { TextExercisePagingService } from 'app/text/manage/text-exercise/service/text-exercise-paging.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';

describe('ExerciseImportComponent', () => {
    setupTestBed({ zoneless: true });

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

    beforeEach(() => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<Exercise | undefined>(),
        } as unknown as DynamicDialogRef;

        TestBed.configureTestingModule({
            providers: [{ provide: DynamicDialogRef, useValue: dialogRef }, { provide: Router, useValue: { navigate: vi.fn() } }, provideHttpClient(), provideHttpClientTesting()],
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

        expect(comp.content).toEqual({ resultsOnPage: [], numberOfPages: 0 });
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

    it('should change the page on active modal', async () => {
        vi.useFakeTimers();
        const defaultPageSize = 10;
        const numberOfPages = 5;
        const pagingServiceSpy = vi.spyOn(quizExercisePagingService, 'search');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages } as SearchResult<TextExercise>));

        fixture.detectChanges();

        let expectedPageNumber = 1;
        comp.onPageChange(expectedPageNumber);
        await vi.advanceTimersByTimeAsync(1);
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total).toBe(numberOfPages * defaultPageSize);

        expectedPageNumber = 2;
        comp.onPageChange(expectedPageNumber);
        await vi.advanceTimersByTimeAsync(1);
        expect(comp.page).toBe(expectedPageNumber);
        expect(comp.total).toBe(numberOfPages * defaultPageSize);

        comp.onPageChange(0);
        await vi.advanceTimersByTimeAsync(1);
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

    it('should set search term and search', async () => {
        vi.useFakeTimers();
        searchStub.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<TextExercise>));

        fixture.detectChanges();

        const expectedSearchTerm = 'search term';
        comp.searchTerm = expectedSearchTerm;
        await vi.advanceTimersByTimeAsync(299);
        expect(comp.searchTerm).toBe(expectedSearchTerm);
        expect(searchStub).not.toHaveBeenCalled();

        await vi.advanceTimersByTimeAsync(1);

        expect(searchStub).toHaveBeenCalledOnce();
    });

    const expectSearchResultAfterAction = async (action: () => void, expectedState: SearchTermPageableSearch) => {
        comp.state = { ...state };
        comp.ngOnInit();
        await vi.advanceTimersByTimeAsync(0);
        searchStub.mockClear();

        action();
        await vi.advanceTimersByTimeAsync(10);
        await vi.advanceTimersByTimeAsync(0);

        expect(searchStub).toHaveBeenCalledWith(expectedState, { isCourseFilter: true, isExamFilter: true, programmingLanguage: undefined });
        expect(comp.content).toEqual(searchResult);
        comp.sortRows();
        expect(sortByPropertyStub).toHaveBeenCalledWith(searchResult.resultsOnPage, comp.sortedColumn, comp.listSorting);
    };

    it('should set content to paging result on sort', async () => {
        vi.useFakeTimers();
        expect(comp.listSorting).toBe(false);

        await expectSearchResultAfterAction(
            () => {
                comp.listSorting = true;
            },
            { ...state, sortingOrder: SortingOrder.ASCENDING },
        );

        expect(comp.listSorting).toBe(true);
    });

    it('should set content to paging result on pageChange', async () => {
        vi.useFakeTimers();
        expect(comp.page).toBe(1);

        await expectSearchResultAfterAction(
            () => {
                comp.onPageChange(5);
            },
            { ...state, page: 5 },
        );

        expect(comp.page).toBe(5);
    });

    it('should set content to paging result on search', async () => {
        vi.useFakeTimers();
        expect(comp.searchTerm).toBe('');
        comp.state = { ...state };
        comp.ngOnInit();
        await vi.advanceTimersByTimeAsync(0);
        searchStub.mockClear();

        const givenSearchTerm = 'givenSearchTerm';
        comp.searchTerm = givenSearchTerm;
        await vi.advanceTimersByTimeAsync(10);
        expect(searchStub).not.toHaveBeenCalled();
        await vi.advanceTimersByTimeAsync(290);
        await vi.advanceTimersByTimeAsync(0);

        expect(searchStub).toHaveBeenCalledWith({ ...state, searchTerm: givenSearchTerm }, { isCourseFilter: true, isExamFilter: true, programmingLanguage: undefined });
        expect(comp.searchTerm).toEqual(givenSearchTerm);
        expect(comp.content).toEqual(searchResult);
        comp.sortRows();
        expect(sortByPropertyStub).toHaveBeenCalledWith(searchResult.resultsOnPage, comp.sortedColumn, comp.listSorting);
    });

    it('should set content to paging result on sortedColumn change', async () => {
        vi.useFakeTimers();
        expect(comp.sortedColumn).toBe('ID');

        await expectSearchResultAfterAction(
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

    it('should switch courseFilter/examFilter and search', async () => {
        vi.useFakeTimers();
        searchStub.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<QuizExercise>));

        fixture.detectChanges();
        expect(comp.isCourseFilter).toBe(true);
        expect(comp.isExamFilter).toBe(true);

        comp.onCourseFilterChange();
        comp.onExamFilterChange();
        await vi.advanceTimersByTimeAsync(299);
        expect(comp.isCourseFilter).toBe(false);
        expect(comp.isExamFilter).toBe(false);
        expect(searchStub).not.toHaveBeenCalled();

        await vi.advanceTimersByTimeAsync(1);

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
    ])('uses the correct paging service', async (exerciseType: ExerciseType, expectedPagingService: any) => {
        vi.useFakeTimers();
        const pagingServiceMock = {
            search: vi.fn().mockReturnValue(of({ numberOfPages: 0, resultsOnPage: [] })),
        };
        const getSpy = vi.spyOn(injector, 'get').mockReturnValue(pagingServiceMock as any);
        fixture.componentRef.setInput('exerciseType', exerciseType);

        comp.ngOnInit();
        await vi.advanceTimersByTimeAsync(300);

        expect(getSpy).toHaveBeenCalledWith(expectedPagingService, {});
        expect(pagingServiceMock.search).toHaveBeenCalled();
    });

    it('should allow importing SCA configurations', async () => {
        vi.useFakeTimers();
        const pagingServiceMock = {
            search: vi.fn().mockReturnValue(of({ numberOfPages: 0, resultsOnPage: [] })),
        };
        const getSpy = vi.spyOn(injector, 'get').mockReturnValue(pagingServiceMock as any);
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        fixture.componentRef.setInput('programmingLanguage', ProgrammingLanguage.JAVA);

        comp.ngOnInit();
        await vi.advanceTimersByTimeAsync(300);

        expect(comp.titleKey).toContain('configureGrading');
        expect(getSpy).toHaveBeenCalledWith(CodeAnalysisPagingService, {});
        expect(pagingServiceMock.search).toHaveBeenCalled();
    });

    it('should sort by exam title when only the exam filter is active', () => {
        comp.isExamFilter = true;
        comp.isCourseFilter = false;

        comp.sortedColumn = 'COURSE_TITLE';

        expect(comp.sortedColumn).toBe('EXAM_TITLE');
    });
});

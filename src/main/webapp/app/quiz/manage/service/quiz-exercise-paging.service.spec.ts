import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClient } from '@angular/common/http';
import { QuizExercisePagingService } from './quiz-exercise-paging.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';

describe('QuizExercisePagingService', () => {
    setupTestBed({ zoneless: true });

    let service: QuizExercisePagingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), QuizExercisePagingService],
        });

        service = TestBed.inject(QuizExercisePagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should search for quiz exercises with course filter', () => {
        const pageable: SearchTermPageableSearch = {
            page: 1,
            pageSize: 10,
            searchTerm: 'test',
            sortedColumn: 'title',
            sortingOrder: SortingOrder.ASCENDING,
        };
        const options = { isCourseFilter: true, isExamFilter: false };

        const mockResult: SearchResult<QuizExercise> = {
            resultsOnPage: [{ id: 1, title: 'Quiz 1' } as QuizExercise],
            numberOfPages: 1,
        };

        service.search(pageable, options).subscribe((result) => {
            expect(result).toEqual(mockResult);
        });

        const req = httpMock.expectOne((request) => {
            return (
                request.url === 'api/quiz/quiz-exercises' &&
                request.params.get('isCourseFilter') === 'true' &&
                request.params.get('isExamFilter') === 'false' &&
                request.params.get('searchTerm') === 'test'
            );
        });

        expect(req.request.method).toBe('GET');
        req.flush(mockResult);
    });

    it('should search for quiz exercises with exam filter', () => {
        const pageable: SearchTermPageableSearch = {
            page: 1,
            pageSize: 10,
            searchTerm: 'exam quiz',
            sortedColumn: 'id',
            sortingOrder: SortingOrder.DESCENDING,
        };
        const options = { isCourseFilter: false, isExamFilter: true };

        const mockResult: SearchResult<QuizExercise> = {
            resultsOnPage: [{ id: 2, title: 'Exam Quiz' } as QuizExercise],
            numberOfPages: 1,
        };

        service.search(pageable, options).subscribe((result) => {
            expect(result).toEqual(mockResult);
        });

        const req = httpMock.expectOne((request) => {
            return (
                request.url === 'api/quiz/quiz-exercises' &&
                request.params.get('isCourseFilter') === 'false' &&
                request.params.get('isExamFilter') === 'true' &&
                request.params.get('searchTerm') === 'exam quiz'
            );
        });

        expect(req.request.method).toBe('GET');
        req.flush(mockResult);
    });

    it('should search with both course and exam filters', () => {
        const pageable: SearchTermPageableSearch = {
            page: 0,
            pageSize: 25,
            searchTerm: '',
            sortedColumn: 'title',
            sortingOrder: SortingOrder.ASCENDING,
        };
        const options = { isCourseFilter: true, isExamFilter: true };

        const mockResult: SearchResult<QuizExercise> = {
            resultsOnPage: [],
            numberOfPages: 0,
        };

        service.search(pageable, options).subscribe((result) => {
            expect(result).toEqual(mockResult);
        });

        const req = httpMock.expectOne((request) => {
            return request.url === 'api/quiz/quiz-exercises' && request.params.get('isCourseFilter') === 'true' && request.params.get('isExamFilter') === 'true';
        });

        expect(req.request.method).toBe('GET');
        req.flush(mockResult);
    });

    it('should handle pagination parameters correctly', () => {
        const pageable: SearchTermPageableSearch = {
            page: 3,
            pageSize: 50,
            searchTerm: 'pagination test',
            sortedColumn: 'dueDate',
            sortingOrder: SortingOrder.DESCENDING,
        };
        const options = { isCourseFilter: true, isExamFilter: false };

        const mockResult: SearchResult<QuizExercise> = {
            resultsOnPage: [],
            numberOfPages: 5,
        };

        service.search(pageable, options).subscribe((result) => {
            expect(result.numberOfPages).toBe(5);
        });

        const req = httpMock.expectOne((request) => {
            return (
                request.url === 'api/quiz/quiz-exercises' &&
                request.params.get('page') === '3' &&
                request.params.get('pageSize') === '50' &&
                request.params.get('sortedColumn') === 'dueDate' &&
                request.params.get('sortingOrder') === 'DESCENDING'
            );
        });

        req.flush(mockResult);
    });
});

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { QuizExercisePagingService } from './quiz-exercise-paging.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';

describe('QuizExercisePagingService', () => {
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
            expect(result.numberOfPages).toBe(mockResult.numberOfPages);
            expect(result.resultsOnPage.map((quizExercise) => [quizExercise.id, quizExercise.title])).toEqual(
                mockResult.resultsOnPage.map((quizExercise) => [quizExercise.id, quizExercise.title]),
            );
            result.resultsOnPage.forEach((quizExercise) => expect(quizExercise).toBeInstanceOf(QuizExercise));
        });

        const req = httpMock.expectOne((request) => {
            return (
                request.url.startsWith('/api/quiz/quiz-exercises') &&
                new URL(request.url, 'http://localhost').searchParams.get('isCourseFilter') === 'true' &&
                new URL(request.url, 'http://localhost').searchParams.get('isExamFilter') === 'false' &&
                new URL(request.url, 'http://localhost').searchParams.get('searchTerm') === 'test'
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
            expect(result.numberOfPages).toBe(mockResult.numberOfPages);
            expect(result.resultsOnPage.map((quizExercise) => [quizExercise.id, quizExercise.title])).toEqual(
                mockResult.resultsOnPage.map((quizExercise) => [quizExercise.id, quizExercise.title]),
            );
            result.resultsOnPage.forEach((quizExercise) => expect(quizExercise).toBeInstanceOf(QuizExercise));
        });

        const req = httpMock.expectOne((request) => {
            return (
                request.url.startsWith('/api/quiz/quiz-exercises') &&
                new URL(request.url, 'http://localhost').searchParams.get('isCourseFilter') === 'false' &&
                new URL(request.url, 'http://localhost').searchParams.get('isExamFilter') === 'true' &&
                new URL(request.url, 'http://localhost').searchParams.get('searchTerm') === 'exam quiz'
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
            expect(result.numberOfPages).toBe(mockResult.numberOfPages);
            expect(result.resultsOnPage.map((quizExercise) => [quizExercise.id, quizExercise.title])).toEqual(
                mockResult.resultsOnPage.map((quizExercise) => [quizExercise.id, quizExercise.title]),
            );
            result.resultsOnPage.forEach((quizExercise) => expect(quizExercise).toBeInstanceOf(QuizExercise));
        });

        const req = httpMock.expectOne((request) => {
            return (
                request.url.startsWith('/api/quiz/quiz-exercises') &&
                new URL(request.url, 'http://localhost').searchParams.get('isCourseFilter') === 'true' &&
                new URL(request.url, 'http://localhost').searchParams.get('isExamFilter') === 'true'
            );
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
                request.url.startsWith('/api/quiz/quiz-exercises') &&
                new URL(request.url, 'http://localhost').searchParams.get('page') === '3' &&
                new URL(request.url, 'http://localhost').searchParams.get('pageSize') === '50' &&
                new URL(request.url, 'http://localhost').searchParams.get('sortedColumn') === 'dueDate' &&
                new URL(request.url, 'http://localhost').searchParams.get('sortingOrder') === 'DESCENDING'
            );
        });

        req.flush(mockResult);
    });
});

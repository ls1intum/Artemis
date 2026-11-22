import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';

/**
 * Wire contract of the exam import search: the same table state must give the same request, and the server JSON of
 * SearchResultPageDTO of ExamForImportListDTO (with its CourseForExamDTO) must give the object that the hand-written
 * client gave, which handed the parsed body through unchanged. The server omits empty fields (NON_EMPTY).
 */
describe('ExamImportPagingService.search wire contract', () => {
    let service: ExamImportPagingService;
    let httpMock: HttpTestingController;

    const page = {
        resultsOnPage: [
            { id: 11, title: 'Real exam', examMode: 'REAL', course: { id: 3, title: 'Algorithms', testCourse: true } },
            { id: 12, examMode: 'TEST_WITH_SUPERVISED_SESSION', course: { id: 4, testCourse: false } },
            { id: 13, title: 'Test exam', examMode: 'TEST' },
        ],
        numberOfPages: 5,
    };

    const pageable = (overrides: Partial<SearchTermPageableSearch> = {}): SearchTermPageableSearch => ({
        page: 4,
        pageSize: 2,
        searchTerm: 'Exam Search',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: 'TITLE',
        ...overrides,
    });

    const expectGet = (): TestRequest => httpMock.expectOne({ method: 'GET' });

    const parse = (req: TestRequest) => {
        const url = new URL(req.request.urlWithParams, 'http://localhost');
        return { path: url.pathname, query: Object.fromEntries(url.searchParams) };
    };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(ExamImportPagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should send the table state of a descending search without exercises', () => {
        service.search(pageable(), { withExercises: false }).subscribe();

        const req = expectGet();
        expect(parse(req)).toEqual({
            path: '/api/exam/exams',
            query: { pageSize: '2', page: '4', sortingOrder: 'DESCENDING', searchTerm: 'Exam Search', sortedColumn: 'TITLE', withExercises: 'false' },
        });
        expect(req.request.body).toBeNull();
        req.flush(page);
    });

    it('should send the table state of an ascending search with exercises and an empty term', () => {
        service.search(pageable({ sortingOrder: SortingOrder.ASCENDING, searchTerm: '', page: 1, pageSize: 50, sortedColumn: 'ID' }), { withExercises: true }).subscribe();

        const req = expectGet();
        expect(parse(req)).toEqual({
            path: '/api/exam/exams',
            query: { pageSize: '50', page: '1', sortingOrder: 'ASCENDING', searchTerm: '', sortedColumn: 'ID', withExercises: 'true' },
        });
        req.flush(page);
    });

    it('should give the page as the server sent it, with exams and courses carrying only the sent fields', () => {
        let result: unknown;
        service.search(pageable(), { withExercises: false }).subscribe((r) => (result = r instanceof HttpResponse ? r.body : r));

        expectGet().flush(page);

        expect(result).toEqual(page);
    });

    it('should give an empty page as the server sent it', () => {
        let result: unknown;
        service.search(pageable(), { withExercises: false }).subscribe((r) => (result = r instanceof HttpResponse ? r.body : r));

        const empty = { resultsOnPage: [], numberOfPages: 0 };
        expectGet().flush(empty);

        expect(result).toEqual(empty);
    });

    it('should propagate the error of the request', () => {
        let status: number | undefined;
        service.search(pageable(), { withExercises: false }).subscribe({ error: (error) => (status = error.status) });

        expectGet().flush(null, { status: 500, statusText: 'Server Error' });

        expect(status).toBe(500);
    });
});

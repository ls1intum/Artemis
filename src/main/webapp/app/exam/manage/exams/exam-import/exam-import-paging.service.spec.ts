import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { SearchResult, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { take } from 'rxjs/operators';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { Exam } from 'app/exam/shared/entities/exam.model';

describe('Exam Import Paging Service', () => {
    let service: ExamImportPagingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }, SessionStorageService, LocalStorageService],
        });
        service = TestBed.inject(ExamImportPagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should request the page and convert the exams', async () => {
        const searchResult = {
            resultsOnPage: [{ id: 1, title: 'RealExam For Testing', examMode: 'REAL', course: { id: 3, title: 'Course', testCourse: false } }],
            numberOfPages: 5,
        };
        const pageable = { pageSize: 2, page: 4, sortingOrder: SortingOrder.DESCENDING, searchTerm: 'ExamSearch', sortedColumn: 'testSortedColumn' };
        let result: SearchResult<Exam> | undefined;
        service
            .search(pageable, { withExercises: false })
            .pipe(take(1))
            .subscribe((resp) => (result = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        const url = new URL(req.request.urlWithParams, 'http://localhost');
        expect(url.pathname).toBe('/api/exam/exams');
        expect(Object.fromEntries(url.searchParams)).toEqual({
            searchTerm: 'ExamSearch',
            withExercises: 'false',
            page: '4',
            pageSize: '2',
            sortingOrder: 'DESCENDING',
            sortedColumn: 'testSortedColumn',
        });
        req.flush(searchResult);
        await Promise.resolve();
        expect(result?.numberOfPages).toBe(5);
        expect(result?.resultsOnPage).toHaveLength(1);
        const exam = result!.resultsOnPage[0];
        // a plain object: the Exam constructor would add defaults the server never sent
        expect(
            Object.entries(exam)
                .filter(([, value]) => value !== undefined)
                .map(([key]) => key)
                .sort(),
        ).toEqual(['course', 'examMode', 'id', 'title']);
        expect(exam.id).toBe(1);
        expect(exam.title).toBe('RealExam For Testing');
        expect(exam.examMode).toBe(ExamMode.REAL);
        expect(exam.course?.id).toBe(3);
        expect(exam.course?.title).toBe('Course');
    });

    it('should send the ascending order and withExercises', async () => {
        service.search({ pageSize: 10, page: 1, sortingOrder: SortingOrder.ASCENDING, searchTerm: '', sortedColumn: 'ID' }, { withExercises: true }).subscribe();
        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.urlWithParams).toContain('withExercises=true');
        expect(req.request.urlWithParams).toContain('sortingOrder=ASCENDING');
        req.flush({});
    });

    it('should convert a missing result into an empty page', async () => {
        let result: SearchResult<Exam> | undefined;
        service.search({ pageSize: 10, page: 1, sortingOrder: SortingOrder.ASCENDING, searchTerm: '', sortedColumn: 'ID' }, { withExercises: true }).subscribe((r) => (result = r));
        httpMock.expectOne({ method: 'GET' }).flush({});
        expect(result).toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });
});

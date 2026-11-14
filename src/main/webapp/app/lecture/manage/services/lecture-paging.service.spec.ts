import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { LecturePagingService } from 'app/lecture/manage/services/lecture-paging.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';

describe('LecturePagingService', () => {
    let service: LecturePagingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(LecturePagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('requests the page with the paging parameters and converts the lectures', () => {
        const pageable: SearchTermPageableSearch = { page: 2, pageSize: 10, searchTerm: 'intro', sortedColumn: 'title', sortingOrder: SortingOrder.DESCENDING };
        let result: SearchResult<Lecture> | undefined;

        service.search(pageable).subscribe((page) => (result = page));
        const req = httpMock.expectOne((request) => request.url.startsWith('/api/lecture/lectures?'));
        const params = new URL(req.request.url, 'http://localhost').searchParams;

        expect(req.request.method).toBe('GET');
        expect(Object.fromEntries(params)).toEqual({ page: '2', pageSize: '10', sortingOrder: 'DESCENDING', sortedColumn: 'title', searchTerm: 'intro' });
        req.flush({ resultsOnPage: [{ id: 1, title: 'Intro', startDate: '2026-10-01T08:00:00Z' }], numberOfPages: 3 });

        expect(result?.numberOfPages).toBe(3);
        expect(result?.resultsOnPage[0]).toBeInstanceOf(Lecture);
        expect(result?.resultsOnPage[0].title).toBe('Intro');
        expect(dayjs.isDayjs(result?.resultsOnPage[0].startDate)).toBe(true);
    });

    it('treats an empty page as no results', () => {
        let result: SearchResult<Lecture> | undefined;
        service.search({ page: 1, pageSize: 10, searchTerm: '', sortedColumn: 'id', sortingOrder: SortingOrder.ASCENDING }).subscribe((page) => (result = page));
        httpMock.expectOne((request) => request.url.startsWith('/api/lecture/lectures')).flush({});

        expect(result).toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });
});

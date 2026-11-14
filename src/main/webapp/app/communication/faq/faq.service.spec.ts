import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { HTTP_INTERCEPTORS, HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { Course } from 'app/course/shared/entities/course.model';
import { CreateFaqDTO, Faq, FaqImportSearchResult, FaqState, UpdateFaqDTO } from 'app/communication/shared/entities/faq.model';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { NotificationInterceptor } from 'app/core/interceptor/notification.interceptor';
import { AlertService } from 'app/foundation/service/alert.service';
import { Faq as FaqResponse } from 'app/openapi/model/faq';
import { FaqImportSearchResult as FaqImportSearchResultResponse } from 'app/openapi/model/faq-import-search-result';

const COURSE_ID = 1;
const BASE = `/api/communication/courses/${COURSE_ID}`;
const ENCODED_CATEGORY = '{"category":"category1","color":"#6ae8ac"}';

/** Splits a generated request URL, which carries its query string, into its path and decoded query parameters. */
function parseUrl(request: TestRequest): { path: string; query: URLSearchParams } {
    const url = new URL(request.request.urlWithParams, 'http://localhost');
    return { path: url.pathname, query: url.searchParams };
}

function expectRequest(httpMock: HttpTestingController, method: string, path: string): TestRequest {
    const request = httpMock.expectOne((req) => req.method === method && new URL(req.urlWithParams, 'http://localhost').pathname === path);
    if (method === 'GET' || method === 'DELETE') {
        expect(request.request.body).toBeNull();
    }
    return request;
}

function faqResponse(overrides: Partial<FaqResponse> = {}): FaqResponse {
    return { id: 1, questionTitle: 'Title', questionAnswer: 'Answer', categories: [ENCODED_CATEGORY], faqState: 'ACCEPTED', ...overrides };
}

function expectedFaq(): Faq {
    const faq = new Faq();
    faq.id = 1;
    faq.questionTitle = 'Title';
    faq.questionAnswer = 'Answer';
    faq.categories = [new FaqCategory('category1', '#6ae8ac')];
    faq.faqState = FaqState.ACCEPTED;
    return faq;
}

describe('Faq Service', () => {
    let httpMock: HttpTestingController;
    let service: FaqService;
    let elemDefault: Faq;
    let createFaqDTODefault: CreateFaqDTO;
    let updateFaqDTODefault: UpdateFaqDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(FaqService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new Faq();
        elemDefault.questionTitle = 'Title';
        elemDefault.course = new Course();
        elemDefault.course.id = COURSE_ID;
        elemDefault.questionAnswer = 'Answer';
        elemDefault.id = 1;
        elemDefault.faqState = FaqState.ACCEPTED;
        elemDefault.categories = [new FaqCategory('category1', '#6ae8ac')];

        createFaqDTODefault = CreateFaqDTO.toCreateFaqDto(elemDefault);
        updateFaqDTODefault = UpdateFaqDTO.toUpdateDto(elemDefault);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('reads', () => {
        it('finds an accepted FAQ and keeps the full response', () => {
            let response: HttpResponse<Faq> | undefined;
            service.find(COURSE_ID, 1).subscribe((res) => (response = res));
            expectRequest(httpMock, 'GET', `${BASE}/faqs/1`).flush(faqResponse(), { headers: { 'X-Frame-Options': 'DENY' } });

            expect(response?.status).toBe(200);
            expect(response?.headers.get('X-Frame-Options')).toBe('DENY');
            expect(response?.url).toBe(`${BASE}/faqs/1`);
            expect(response?.body).toBeInstanceOf(Faq);
            expect(response?.body).toEqual(expectedFaq());
            expect(response?.body?.categories?.[0]).toBeInstanceOf(FaqCategory);
        });

        it('loads the management detail of a proposed FAQ without answer and categories', () => {
            let response: HttpResponse<Faq> | undefined;
            service.findForManagement(COURSE_ID, 2).subscribe((res) => (response = res));
            expectRequest(httpMock, 'GET', `${BASE}/faqs/2/for-management`).flush({ id: 2, questionTitle: 'Proposal', faqState: 'PROPOSED' } satisfies FaqResponse);

            expect(response?.ok).toBe(true);
            expect(response?.body?.faqState).toBe(FaqState.PROPOSED);
            expect(response?.body?.questionAnswer).toBeUndefined();
            expect(response?.body?.categories).toBeUndefined();
        });

        it('keeps a null body as null', () => {
            let response: HttpResponse<Faq> | undefined;
            service.findForManagement(COURSE_ID, 2).subscribe((res) => (response = res));
            expectRequest(httpMock, 'GET', `${BASE}/faqs/2/for-management`).flush(null);

            expect(response?.body).toBeNull();
        });

        it.each([undefined, FaqState.ACCEPTED, FaqState.PROPOSED, FaqState.REJECTED])('loads the management list with state %s', (state) => {
            let response: HttpResponse<Faq[]> | undefined;
            service.findAllForManagement(COURSE_ID, state).subscribe((res) => (response = res));
            const request = expectRequest(httpMock, 'GET', `${BASE}/faqs/for-management`);
            const { query } = parseUrl(request);
            expect(query.get('faqState')).toBe(state ?? null);
            expect([...query.keys()]).toHaveLength(state ? 1 : 0);
            request.flush([faqResponse()]);

            expect(response?.body).toEqual([expectedFaq()]);
        });

        it.each([undefined, FaqState.ACCEPTED, FaqState.PROPOSED, FaqState.REJECTED])('loads the management categories with state %s', (state) => {
            let response: HttpResponse<string[]> | undefined;
            service.findAllCategoriesForManagement(COURSE_ID, state).subscribe((res) => (response = res));
            const request = expectRequest(httpMock, 'GET', `${BASE}/faq-categories/for-management`);
            expect(parseUrl(request).query.get('faqState')).toBe(state ?? null);
            request.flush([ENCODED_CATEGORY]);

            expect(response?.body).toEqual([ENCODED_CATEGORY]);
        });

        it('finds the accepted FAQs of a course', () => {
            let response: HttpResponse<Faq[]> | undefined;
            service.findAllByCourseId(COURSE_ID).subscribe((res) => (response = res));
            expectRequest(httpMock, 'GET', `${BASE}/faqs`).flush([faqResponse(), faqResponse({ id: 2, questionAnswer: undefined, categories: undefined })]);

            expect(response?.body).toHaveLength(2);
            expect(response?.body?.[0]).toEqual(expectedFaq());
            expect(response?.body?.[1].id).toBe(2);
            expect(response?.body?.[1].categories).toBeUndefined();
        });

        it('keeps an empty FAQ list', () => {
            let response: HttpResponse<Faq[]> | undefined;
            service.findAllByCourseId(COURSE_ID).subscribe((res) => (response = res));
            expectRequest(httpMock, 'GET', `${BASE}/faqs`).flush([]);

            expect(response?.body).toEqual([]);
        });

        it.each(Object.values(FaqState))('finds FAQs by state %s on the state path', (state) => {
            let response: HttpResponse<Faq[]> | undefined;
            service.findAllByCourseIdAndState(COURSE_ID, state).subscribe((res) => (response = res));
            expectRequest(httpMock, 'GET', `${BASE}/faq-state/${state}`).flush([faqResponse()]);

            expect(response?.body).toEqual([expectedFaq()]);
        });

        it('passes a forbidden state error on to the caller', () => {
            let error: HttpErrorResponse | undefined;
            service.findAllByCourseIdAndState(COURSE_ID, FaqState.PROPOSED).subscribe({ error: (err: HttpErrorResponse) => (error = err) });
            expectRequest(httpMock, 'GET', `${BASE}/faq-state/PROPOSED`).flush(null, { status: 403, statusText: 'Forbidden' });

            expect(error?.status).toBe(403);
        });

        it('finds the accepted categories of a course', () => {
            let response: HttpResponse<string[]> | undefined;
            service.findAllCategoriesByCourseId(COURSE_ID).subscribe((res) => (response = res));
            expectRequest(httpMock, 'GET', `${BASE}/faq-categories`).flush([ENCODED_CATEGORY], { headers: { 'X-Frame-Options': 'DENY' } });

            expect(response?.body).toEqual([ENCODED_CATEGORY]);
            expect(response?.headers.get('X-Frame-Options')).toBe('DENY');
        });

        it.each(Object.values(FaqState))('finds the categories by state %s on the state path', (state) => {
            let response: HttpResponse<string[]> | undefined;
            service.findAllCategoriesByCourseIdAndCategory(COURSE_ID, state).subscribe((res) => (response = res));
            expectRequest(httpMock, 'GET', `${BASE}/faq-categories/${state}`).flush([]);

            expect(response?.body).toEqual([]);
        });

        it('sends one request per subscription', () => {
            const request = service.find(COURSE_ID, 1);
            httpMock.expectNone(`${BASE}/faqs/1`);
            request.subscribe();
            request.subscribe();
            const requests = httpMock.match((req) => req.urlWithParams === `${BASE}/faqs/1`);
            expect(requests).toHaveLength(2);
            requests.forEach((req) => req.flush(faqResponse()));
        });
    });

    describe('writes', () => {
        it('creates a FAQ with encoded categories and keeps the created status and location', () => {
            let response: HttpResponse<Faq> | undefined;
            service.create(COURSE_ID, createFaqDTODefault).subscribe((res) => (response = res));
            const request = expectRequest(httpMock, 'POST', `${BASE}/faqs`);
            expect(request.request.body).toEqual({
                courseId: COURSE_ID,
                questionTitle: 'Title',
                questionAnswer: 'Answer',
                categories: [ENCODED_CATEGORY],
                faqState: 'ACCEPTED',
            });
            request.flush(faqResponse({ id: 5 }), { status: 201, statusText: 'Created', headers: { Location: `${BASE}/faqs/5` } });

            expect(response?.status).toBe(201);
            expect(response?.headers.get('Location')).toBe(`${BASE}/faqs/5`);
            expect(response?.body?.id).toBe(5);
            expect(response?.body?.categories).toEqual([new FaqCategory('category1', '#6ae8ac')]);
            expect(createFaqDTODefault.categories?.[0]).toBeInstanceOf(FaqCategory);
        });

        it('passes a course ID that differs from the path on to the server', () => {
            const createFaqDTO = new CreateFaqDTO(FaqState.PROPOSED, 'Title', 2);
            service.create(COURSE_ID, createFaqDTO).subscribe({ error: () => undefined });
            const request = expectRequest(httpMock, 'POST', `${BASE}/faqs`);
            expect(request.request.body).toEqual({ courseId: 2, questionTitle: 'Title', faqState: 'PROPOSED' });
            request.flush({ title: 'Course ID in path and FAQ do not match' }, { status: 400, statusText: 'Bad Request' });
        });

        it('rejects a FAQ without its course before sending it', () => {
            let error: unknown;
            service.create(COURSE_ID, new CreateFaqDTO(FaqState.PROPOSED, 'Title')).subscribe({ error: (err: unknown) => (error = err) });
            httpMock.expectNone(() => true);

            expect(error).toBeInstanceOf(Error);
        });

        it('updates a FAQ at the path of its ID', () => {
            let response: HttpResponse<Faq> | undefined;
            service.update(COURSE_ID, updateFaqDTODefault).subscribe((res) => (response = res));
            const request = expectRequest(httpMock, 'PUT', `${BASE}/faqs/1`);
            expect(request.request.body).toEqual({ id: 1, questionTitle: 'Title', questionAnswer: 'Answer', categories: [ENCODED_CATEGORY], faqState: 'ACCEPTED' });
            request.flush(faqResponse({ questionTitle: 'Updated' }));

            expect(response?.status).toBe(200);
            expect(response?.body?.questionTitle).toBe('Updated');
            expect(response?.body?.categories?.[0]).toBeInstanceOf(FaqCategory);
        });

        it('deletes a FAQ and keeps the empty response with its alert headers', () => {
            let response: HttpResponse<void> | undefined;
            service.delete(COURSE_ID, 1).subscribe((res) => (response = res));
            expectRequest(httpMock, 'DELETE', `${BASE}/faqs/1`).flush(null, { headers: { 'X-artemisApp-alert': 'artemisApp.faq.deleted', 'X-artemisApp-params': '1' } });

            expect(response?.status).toBe(200);
            expect(response?.body).toBeNull();
            expect(response?.headers.get('X-artemisApp-alert')).toBe('artemisApp.faq.deleted');
            expect(response?.headers.get('X-artemisApp-params')).toBe('1');
        });
    });

    describe('import', () => {
        it('searches importable FAQs with the target course and page', () => {
            let received: FaqImportSearchResult | undefined;
            service.searchForImport(42, 'exam dates', 2).subscribe((result) => (received = result));
            const request = expectRequest(httpMock, 'GET', '/api/communication/courses/42/faqs/for-import');
            const { query } = parseUrl(request);
            expect(query.get('searchTerm')).toBe('exam dates');
            expect(query.get('page')).toBe('2');
            request.flush({ numberOfPages: 0, totalElements: 0 } satisfies FaqImportSearchResultResponse);

            expect(received).toEqual({ resultsOnPage: [], numberOfPages: 0, totalElements: 0 });
        });

        it('keeps an empty search term, the first page and special characters', () => {
            service.searchForImport(42, '', 0).subscribe();
            const empty = parseUrl(expectRequest(httpMock, 'GET', '/api/communication/courses/42/faqs/for-import'));
            expect(empty.query.get('searchTerm')).toBe('');
            expect(empty.query.get('page')).toBe('0');
            httpMock.match(() => true).forEach((req) => req.flush({ numberOfPages: 0, totalElements: 0 }));

            const term = 'C++ & "50%" / ä?';
            service.searchForImport(42, term, 1).subscribe();
            const special = expectRequest(httpMock, 'GET', '/api/communication/courses/42/faqs/for-import');
            expect(parseUrl(special).query.get('searchTerm')).toBe(term);
            special.flush({ numberOfPages: 0, totalElements: 0 });
        });

        it('preserves source-course metadata and exact totals in import search results', () => {
            const result: FaqImportSearchResultResponse = {
                resultsOnPage: [
                    {
                        id: 10,
                        questionTitle: 'Exam registration',
                        questionAnswer: 'Register online',
                        faqState: 'ACCEPTED',
                        courseId: 5,
                        courseTitle: 'Previous course',
                        courseSemester: 'WS25',
                    },
                    { id: 11, questionTitle: 'Proposal', faqState: 'PROPOSED', courseId: 6 },
                ],
                numberOfPages: 3,
                totalElements: 24,
            };
            let received: FaqImportSearchResult | undefined;
            service.searchForImport(42, 'exam', 1).subscribe((response) => (received = response));
            expectRequest(httpMock, 'GET', '/api/communication/courses/42/faqs/for-import').flush(result);

            expect(received).toEqual({
                resultsOnPage: [
                    {
                        id: 10,
                        questionTitle: 'Exam registration',
                        questionAnswer: 'Register online',
                        faqState: FaqState.ACCEPTED,
                        courseId: 5,
                        courseTitle: 'Previous course',
                        courseSemester: 'WS25',
                    },
                    { id: 11, questionTitle: 'Proposal', faqState: FaqState.PROPOSED, courseId: 6 },
                ],
                numberOfPages: 3,
                totalElements: 24,
            });
        });

        it('imports selected IDs, decodes the copied FAQ categories and keeps the created response', () => {
            let response: HttpResponse<Faq[]> | undefined;
            service.importFaqs(42, [10, 20]).subscribe((res) => (response = res));
            const request = expectRequest(httpMock, 'POST', '/api/communication/courses/42/faqs/import');
            expect(request.request.body).toEqual({ faqIds: [10, 20] });
            request.flush([faqResponse({ id: 31, categories: ['{"category":"Exams","color":"#123456"}'] })], {
                status: 201,
                statusText: 'Created',
                headers: { Location: '/api/communication/courses/42/faqs' },
            });

            expect(response?.status).toBe(201);
            expect(response?.headers.get('Location')).toBe('/api/communication/courses/42/faqs');
            expect(response?.body?.[0].id).toBe(31);
            expect(response?.body?.[0].categories).toEqual([new FaqCategory('Exams', '#123456')]);
        });
    });

    describe('helpers', () => {
        it('should set add active filter correctly', () => {
            let activeFilters = new Set<string>();
            activeFilters = service.toggleFilter('category1', activeFilters);

            expect(activeFilters).toContain('category1');
            expect(activeFilters.size).toBe(1);
        });

        it('should remove active filter correctly', () => {
            let activeFilters = new Set<string>();
            activeFilters.add('category1');
            activeFilters = service.toggleFilter('category1', activeFilters);

            expect(activeFilters).not.toContain('category1');
            expect(activeFilters.size).toBe(0);
        });

        it('should apply faqFilter correctly', () => {
            const activeFilters = new Set<string>();

            const faq1 = new Faq();
            faq1.categories = [new FaqCategory('test', 'red'), new FaqCategory('test2', 'blue')];

            const faq11 = new Faq();
            faq11.categories = [new FaqCategory('test', 'red'), new FaqCategory('test2', 'blue')];

            const faq2 = new Faq();
            faq2.categories = [new FaqCategory('testing', 'red'), new FaqCategory('test2', 'blue')];

            let filteredFaq = [faq1, faq11, faq2];

            filteredFaq = service.applyFilters(activeFilters, filteredFaq);
            expect(filteredFaq).toHaveLength(3);
            expect(filteredFaq).toEqual(expect.arrayContaining([faq1, faq11, faq2]));

            activeFilters.add('test');
            filteredFaq = service.applyFilters(activeFilters, filteredFaq);
            expect(filteredFaq).toHaveLength(2);
            expect(filteredFaq).toEqual(expect.arrayContaining([faq1, faq11]));
        });

        it('should convert stringified categories from server into FaqCategory objects', () => {
            const convertedCategory = service.convertFaqCategoriesAsStringFromServer(['{"category":"category1", "color":"red"}']);
            expect(convertedCategory[0].category).toBe('category1');
            expect(convertedCategory[0].color).toBe('red');
        });

        it('should return if all tokens exist in FAQ title or answer', () => {
            const faq1 = new Faq();
            faq1.questionTitle = 'Title';
            faq1.questionAnswer = 'Answer';

            expect(service.hasSearchTokens(faq1, 'title answer')).toBe(true);
            expect(service.hasSearchTokens(faq1, 'title answer missing')).toBe(false);
        });

        it('does not match missing FAQ content as the word undefined', () => {
            expect(service.hasSearchTokens({ questionTitle: 'Exam date' }, 'undefined')).toBe(false);
            expect(service.hasSearchTokens({}, 'exam')).toBe(false);
        });

        it('excludes FAQs without categories when a category filter is active', () => {
            expect(service.applyFilters(new Set(['Exams']), [{ id: 31 }])).toEqual([]);
        });

        it('should stringify categories without mutating the original createFaqDTO', () => {
            const originalRef = createFaqDTODefault.categories;

            const request = FaqService.convertCreateFaqFromClient(createFaqDTODefault, COURSE_ID);

            expect(createFaqDTODefault.categories).toBe(originalRef);
            expect(createFaqDTODefault.categories?.[0]).toEqual(new FaqCategory('category1', '#6ae8ac'));
            expect(request).toEqual({ courseId: COURSE_ID, questionTitle: 'Title', questionAnswer: 'Answer', categories: [ENCODED_CATEGORY], faqState: 'ACCEPTED' });
        });

        it('should stringify categories without mutating the original updateFaqDTO', () => {
            const originalRef = updateFaqDTODefault.categories;

            const request = FaqService.convertUpdateFaqFromClient(updateFaqDTODefault);

            expect(updateFaqDTODefault.categories).toBe(originalRef);
            expect(updateFaqDTODefault.categories?.[0]).toEqual(new FaqCategory('category1', '#6ae8ac'));
            expect(request.categories).toEqual([ENCODED_CATEGORY]);
        });

        it('does not encode already serialized categories twice', () => {
            const dto = new CreateFaqDTO(FaqState.PROPOSED, 'Title', COURSE_ID, [ENCODED_CATEGORY]);
            expect(FaqService.stringifyFaqCategories(dto)).toEqual([ENCODED_CATEGORY]);
        });

        it('should return undefined when DTO has no categories', () => {
            const noCats = new CreateFaqDTO(createFaqDTODefault.faqState, createFaqDTODefault.questionTitle, COURSE_ID, undefined, createFaqDTODefault.questionAnswer);
            expect(FaqService.stringifyFaqCategories(noCats)).toBeUndefined();
            expect(FaqService.convertCreateFaqFromClient(noCats, COURSE_ID).categories).toBeUndefined();
        });

        it('should throw if faqState is missing when converting to CreateFaqDto', () => {
            const f = new Faq();
            f.questionTitle = 'Title';
            expect(() => CreateFaqDTO.toCreateFaqDto(f)).toThrow('The state should be present to create FAQ');
        });

        it('should throw if id is missing when converting to UpdateFaqDTO', () => {
            const f = new Faq();
            f.faqState = FaqState.ACCEPTED;
            f.questionTitle = 'Title';
            expect(() => UpdateFaqDTO.toUpdateDto(f)).toThrow('The id should be present to update FAQ');
        });

        it('should throw if faqState is missing when converting to UpdateFaqDTO', () => {
            const f = new Faq();
            f.id = 99;
            f.questionTitle = 'Title';
            expect(() => UpdateFaqDTO.toUpdateDto(f)).toThrow('The state should be present to update FAQ');
        });
    });
});

describe('Faq Service with the notification interceptor', () => {
    it('shows the translated deletion alert with the decoded FAQ ID', () => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                { provide: HTTP_INTERCEPTORS, useClass: NotificationInterceptor, multi: true },
                MockProvider(AlertService),
            ],
        });
        const service = TestBed.inject(FaqService);
        const httpMock = TestBed.inject(HttpTestingController);
        const success = vi.spyOn(TestBed.inject(AlertService), 'success');

        service.delete(COURSE_ID, 7).subscribe();
        httpMock
            .expectOne(`${BASE}/faqs/7`)
            .flush(null, { headers: new HttpHeaders({ 'X-artemisApp-alert': 'artemisApp.faq.deleted', 'X-artemisApp-params': encodeURIComponent('7') }) });

        expect(success).toHaveBeenCalledOnce();
        expect(success).toHaveBeenCalledWith('artemisApp.faq.deleted', { param: '7' });
        httpMock.verify();
    });
});

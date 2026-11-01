import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { take } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/course/shared/entities/course.model';
import { CreateFaqDTO, Faq, FaqState, UpdateFaqDTO } from 'app/communication/shared/entities/faq.model';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';

describe('Faq Service', () => {
    let httpMock: HttpTestingController;
    let service: FaqService;
    let expectedResult: any;
    let elemDefault: Faq;
    let courseId: number;
    let createFaqDTODefault: CreateFaqDTO;
    let updateFaqDTODefault: UpdateFaqDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(FaqService);
        httpMock = TestBed.inject(HttpTestingController);

        expectedResult = {} as HttpResponse<Faq>;
        elemDefault = new Faq();
        elemDefault.questionTitle = 'Title';
        elemDefault.course = new Course();
        elemDefault.questionAnswer = 'Answer';
        elemDefault.id = 1;
        elemDefault.faqState = FaqState.ACCEPTED;
        elemDefault.categories = [new FaqCategory('category1', '#6ae8ac')];
        courseId = 1;

        createFaqDTODefault = CreateFaqDTO.toCreateFaqDto(elemDefault);
        updateFaqDTODefault = UpdateFaqDTO.toUpdateDto(elemDefault);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it.each([undefined, FaqState.PROPOSED, FaqState.REJECTED])('loads management list with data-only state %s', (state) => {
        service.findAllForManagement(courseId, state).subscribe((res) => expect(res.body?.[0].categories).toEqual(elemDefault.categories));
        const req = httpMock.expectOne((request) => request.url === `api/communication/courses/${courseId}/faqs/for-management`);
        expect(req.request.params.get('faqState')).toBe(state ?? null);
        req.flush([{ ...elemDefault, categories: [JSON.stringify(elemDefault.categories![0])] }]);
    });

    it('loads management detail and converts categories', () => {
        service.findForManagement(courseId, 1).subscribe((res) => expect(res.body?.categories).toEqual(elemDefault.categories));
        httpMock.expectOne(`api/communication/courses/${courseId}/faqs/1/for-management`).flush({ ...elemDefault, categories: [JSON.stringify(elemDefault.categories![0])] });
    });

    it.each([undefined, FaqState.PROPOSED, FaqState.REJECTED])('loads management categories with state %s', (state) => {
        service.findAllCategoriesForManagement(courseId, state).subscribe((res) => expect(res.body).toEqual(['category']));
        const req = httpMock.expectOne((request) => request.url === `api/communication/courses/${courseId}/faq-categories/for-management`);
        expect(req.request.params.get('faqState')).toBe(state ?? null);
        req.flush(['category']);
    });

    describe('Service methods', () => {
        it('should create a faq', () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .create(courseId, createFaqDTODefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/communication/courses/${courseId}/faqs`,
                method: 'POST',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should update a faq', () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            const faqId = elemDefault.id!;
            service
                .update(courseId, updateFaqDTODefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/communication/courses/${courseId}/faqs/${faqId}`,
                method: 'PUT',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should delete a faq', () => {
            const returnedFromService = { ...elemDefault };
            const faqId = elemDefault.id!;
            service
                .delete(courseId, faqId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/communication/courses/${courseId}/faqs/${faqId}`,
                method: 'DELETE',
            });
            req.flush(returnedFromService);
            expect(req.request.method).toBe('DELETE');
        });

        it('should find a faq', () => {
            const category = {
                color: '#6ae8ac',
                category: 'category1',
            } as FaqCategory;
            const returnedFromService = { ...elemDefault, categories: [JSON.stringify(category)] };
            const expected = { ...elemDefault, categories: [new FaqCategory('category1', '#6ae8ac')] };
            const faqId = elemDefault.id!;
            service
                .find(courseId, faqId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/communication/courses/${courseId}/faqs/${faqId}`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find faqs by courseId', () => {
            const category = {
                color: '#6ae8ac',
                category: 'category1',
            } as FaqCategory;
            const returnedFromService = [{ ...elemDefault, categories: [JSON.stringify(category)] }];
            const expected = [{ ...elemDefault, categories: [new FaqCategory('category1', '#6ae8ac')] }];
            const courseId = 1;
            service
                .findAllByCourseId(courseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/communication/courses/${courseId}/faqs`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find faqs by courseId and status', () => {
            const category = {
                color: '#6ae8ac',
                category: 'category1',
            } as FaqCategory;
            const returnedFromService = [{ ...elemDefault, categories: [JSON.stringify(category)] }];
            const expected = [{ ...elemDefault, categories: [new FaqCategory('category1', '#6ae8ac')] }];
            const courseId = 1;
            service
                .findAllByCourseIdAndState(courseId, FaqState.ACCEPTED)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/communication/courses/${courseId}/faq-state/${FaqState.ACCEPTED}`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find all categories by courseId', () => {
            const category = {
                color: '#6ae8ac',
                category: 'category1',
            } as FaqCategory;
            const returnedFromService = { categories: [JSON.stringify(category)] };
            const expected = { ...returnedFromService };
            const courseId = 1;
            service
                .findAllCategoriesByCourseId(courseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/communication/courses/${courseId}/faq-categories`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find all categories by courseId and faqState', () => {
            const category = {
                color: '#6ae8ac',
                category: 'category1',
            } as FaqCategory;
            const returnedFromService = { categories: [JSON.stringify(category)] };
            const expected = { ...returnedFromService };
            const courseId = 1;
            const faqState = FaqState.ACCEPTED;
            service
                .findAllCategoriesByCourseIdAndCategory(courseId, faqState)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/communication/courses/${courseId}/faq-categories/${faqState}`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

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
    });
    it('should stringify categories without mutating the original createFaqDTO', () => {
        const original = createFaqDTODefault;
        const originalRef = original.categories;

        const copy = FaqService.convertCreateFaqFromClient(original);

        expect(original.categories).toBe(originalRef);
        expect((original.categories?.[0] as any).category).toBe('category1');
        expect(typeof (copy.categories?.[0] as unknown as string)).toBe('string');
        const parsed = JSON.parse(copy.categories?.[0] as unknown as string);
        expect(parsed).toEqual({ category: 'category1', color: '#6ae8ac' });
    });

    it('should stringify categories without mutating the original updateFaqDTO', () => {
        const original = updateFaqDTODefault;
        const originalRef = original.categories;

        const copy = FaqService.convertUpdateFaqFromClient(original);

        expect(original.categories).toBe(originalRef);
        expect(typeof (copy.categories?.[0] as unknown as string)).toBe('string');
        expect(JSON.parse(copy.categories?.[0] as unknown as string)).toEqual({ category: 'category1', color: '#6ae8ac' });
        expect((original.categories?.[0] as any).category).toBe('category1');
    });

    it('should return undefined when DTO has no categories', () => {
        const noCats = new CreateFaqDTO(createFaqDTODefault.faqState, createFaqDTODefault.questionTitle, courseId, undefined, createFaqDTODefault.questionAnswer);
        const res = FaqService.stringifyFaqCategories(noCats);
        expect(res).toBeUndefined();
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
    it('searches importable FAQs with the target course and page', () => {
        service.searchForImport(42, 'exam dates', 2).subscribe((result) => {
            expect(result.resultsOnPage).toEqual([]);
            expect(result.numberOfPages).toBe(0);
            expect(result.totalElements).toBe(0);
        });
        const req = httpMock.expectOne((request) => request.url === 'api/communication/courses/42/faqs/for-import');
        expect(req.request.method).toBe('GET');
        expect(req.request.params.get('searchTerm')).toBe('exam dates');
        expect(req.request.params.get('page')).toBe('2');
        req.flush({});
    });

    it('imports selected IDs and decodes the copied FAQ categories', () => {
        service.importFaqs(42, [10, 20]).subscribe((result) => {
            expect(result.body?.[0].categories).toEqual([{ category: 'Exams', color: '#123456' }]);
        });
        const req = httpMock.expectOne('api/communication/courses/42/faqs/import');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual({ faqIds: [10, 20] });
        req.flush([{ id: 31, categories: ['{"category":"Exams","color":"#123456"}'] }]);
    });
    it('preserves source-course metadata and exact totals in import search results', () => {
        const result = {
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
            ],
            numberOfPages: 3,
            totalElements: 24,
        };
        let received: unknown;
        service.searchForImport(42, 'exam', 1).subscribe((response) => (received = response));
        httpMock.expectOne((request) => request.url.endsWith('/42/faqs/for-import')).flush(result);
        expect(received).toEqual(result);
    });
    it('does not match missing FAQ content as the word undefined', () => {
        expect(service.hasSearchTokens({ questionTitle: 'Exam date' }, 'undefined')).toBe(false);
        expect(service.hasSearchTokens({}, 'exam')).toBe(false);
    });

    it('excludes FAQs without categories when a category filter is active', () => {
        expect(service.applyFilters(new Set(['Exams']), [{ id: 31 }])).toEqual([]);
    });
});

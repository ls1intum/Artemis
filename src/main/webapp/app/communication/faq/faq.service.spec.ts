import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Faq, FaqState } from 'app/communication/shared/entities/faq.model';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { EMPTY, of } from 'rxjs';

describe('Faq Service', () => {
    let httpMock: HttpTestingController;
    let service: FaqService;
    let expectedResult: any;
    let elemDefault: Faq;
    let courseId: number;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
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
        courseId = 1;
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should create a faq', () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .create(courseId, elemDefault)
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
                .update(courseId, elemDefault)
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
            expect(filteredFaq).toBeArrayOfSize(3);
            expect(filteredFaq).toIncludeAllMembers([faq1, faq11, faq2]);

            activeFilters.add('test');
            filteredFaq = service.applyFilters(activeFilters, filteredFaq);
            expect(filteredFaq).toBeArrayOfSize(2);
            expect(filteredFaq).toIncludeAllMembers([faq1, faq11]);
        });

        it('should convert String into FAQ categories   correctly', async () => {
            const convertedCategory = service.convertFaqCategoriesAsStringFromServer(['{"category":"category1", "color":"red"}']);
            expect(convertedCategory[0].category).toBe('category1');
            expect(convertedCategory[0].color).toBe('red');
        });

        it('should convert FAQ categories into strings', () => {
            const faq2 = new Faq();
            faq2.categories = [new FaqCategory('testing', 'red')];
            const convertedCategory = FaqService.stringifyFaqCategories(faq2);
            expect(convertedCategory).toEqual(['{"color":"red","category":"testing"}']);
        });

        it('should return if all tokens exist in FAQ title or answer', () => {
            const faq1 = new Faq();
            faq1.questionTitle = 'Title';
            faq1.questionAnswer = 'Answer';

            expect(service.hasSearchTokens(faq1, 'title answer')).toBeTrue();
            expect(service.hasSearchTokens(faq1, 'title answer missing')).toBeFalse();
        });

        it('should send a POST request to ingest faqs and return an OK response', () => {
            const courseId = 123;
            const expectedUrl = `api/communication/courses/${courseId}/faqs/ingest`;
            const expectedStatus = 200;

            service.ingestFaqsInPyris(courseId).subscribe((response) => {
                expect(response.status).toBe(expectedStatus);
            });

            const req = httpMock.expectOne({
                url: expectedUrl,
                method: 'POST',
            });
            expect(req.request.method).toBe('POST');
        });
    });
    it('should make PUT request to enable FAQ', () => {
        service.enable(1).subscribe((resp) => expect(resp).toEqual(of(EMPTY)));
        httpMock.expectOne({ method: 'PUT', url: `api/communication/courses/1/faqs/enable` });
    });
});

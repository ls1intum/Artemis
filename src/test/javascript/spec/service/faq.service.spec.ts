import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { Course } from 'app/entities/course.model';
import { FAQ, FAQState } from 'app/entities/faq.model';
import { FAQCategory } from 'app/entities/faq-category.model';
import { FAQService } from 'app/faq/faq.service';

describe('Faq Service', () => {
    let httpMock: HttpTestingController;
    let service: FAQService;
    const resourceUrl = 'api/faqs';
    let expectedResult: any;
    let elemDefault: FAQ;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(FAQService);
        httpMock = TestBed.inject(HttpTestingController);

        expectedResult = {} as HttpResponse<FAQ>;
        elemDefault = new FAQ();
        elemDefault.questionTitle = 'Title';
        elemDefault.course = new Course();
        elemDefault.questionAnswer = 'Answer';
        elemDefault.id = 1;
        elemDefault.faqState = FAQState.ACCEPTED;
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should create a faq', () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .create(elemDefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: resourceUrl,
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
                .update(elemDefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${faqId}`,
                method: 'PUT',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should delete a faq', () => {
            const returnedFromService = { ...elemDefault };
            const faqId = elemDefault.id!;
            service
                .delete(faqId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${faqId}`,
                method: 'DELETE',
            });
            req.flush(returnedFromService);
            expect(req.request.method).toBe('DELETE');
        });

        it('should find a faq', () => {
            const category = {
                color: '#6ae8ac',
                category: 'category1',
            } as FAQCategory;
            const returnedFromService = { ...elemDefault, categories: [JSON.stringify(category)] };
            const expected = { ...elemDefault, categories: [new FAQCategory('category1', '#6ae8ac')] };
            const faqId = elemDefault.id!;
            service
                .find(faqId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${faqId}`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find faqs by courseId', async () => {
            const category = {
                color: '#6ae8ac',
                category: 'category1',
            } as FAQCategory;
            const returnedFromService = [{ ...elemDefault, categories: [JSON.stringify(category)] }];
            const expected = [{ ...elemDefault, categories: [new FAQCategory('category1', '#6ae8ac')] }];
            const courseId = 1;
            service
                .findAllByCourseId(courseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/courses/${courseId}/faqs`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find all categories by courseId', async () => {
            const category = {
                color: '#6ae8ac',
                category: 'category1',
            } as FAQCategory;
            const returnedFromService = { categories: [JSON.stringify(category)] };
            const expected = { ...returnedFromService };
            const courseId = 1;
            service
                .findAllCategoriesByCourseId(courseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/courses/${courseId}/faq-categories`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should set add active filter correctly', async () => {
            let activeFilters = new Set<string>();
            activeFilters = service.toggleFilter('category1', activeFilters);

            expect(activeFilters).toContain('category1');
            expect(activeFilters.size).toBe(1);
        });

        it('should remove active filter correctly', async () => {
            let activeFilters = new Set<string>();
            activeFilters.add('category1');
            activeFilters = service.toggleFilter('category1', activeFilters);

            expect(activeFilters).not.toContain('category1');
            expect(activeFilters.size).toBe(0);
        });

        it('should apply faqFilter  correctly', async () => {
            const activeFilters = new Set<string>();

            const faq1 = new FAQ();
            faq1.categories = [new FAQCategory('test', 'red'), new FAQCategory('test2', 'blue')];

            const faq11 = new FAQ();
            faq11.categories = [new FAQCategory('test', 'red'), new FAQCategory('test2', 'blue')];

            const faq2 = new FAQ();
            faq2.categories = [new FAQCategory('testing', 'red'), new FAQCategory('test2', 'blue')];

            let filteredFaq = [faq1, faq11, faq2];

            filteredFaq = service.applyFilters(activeFilters, filteredFaq);
            expect(filteredFaq).toBeArrayOfSize(3);
            expect(filteredFaq).toContainAllValues([faq1, faq11, faq2]);

            activeFilters.add('test');
            filteredFaq = service.applyFilters(activeFilters, filteredFaq);
            expect(filteredFaq).toBeArrayOfSize(2);
            expect(filteredFaq).toContainAllValues([faq1, faq11]);
        });

        it('should convert String into FAQ categories   correctly', async () => {
            const convertedCategory = service.convertFaqCategoriesAsStringFromServer(['{"category":"category1", "color":"red"}']);
            expect(convertedCategory[0].category).toBe('category1');
            expect(convertedCategory[0].color).toBe('red');
        });

        it('should convert FAQ categories into strings', async () => {
            const faq2 = new FAQ();
            faq2.categories = [new FAQCategory('testing', 'red')];
            const convertedCategory = FAQService.stringifyFaqCategories(faq2);
            expect(convertedCategory).toEqual(['{"color":"red","category":"testing"}']);
        });
    });
});

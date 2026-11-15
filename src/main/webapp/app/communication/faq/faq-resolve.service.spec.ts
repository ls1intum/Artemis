import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { FaqResolve } from './faq-resolve.service';
import { Faq, FaqState } from 'app/communication/shared/entities/faq.model';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { Faq as FaqResponse } from 'app/openapi/model/faq';

describe('FAQ management resolver', () => {
    let http: HttpTestingController;
    let resolver: FaqResolve;

    beforeEach(() => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        http = TestBed.inject(HttpTestingController);
        resolver = TestBed.inject(FaqResolve);
    });

    afterEach(() => http.verify());

    it('loads proposed FAQ from management detail for the edit route', () => {
        const route = new ActivatedRouteSnapshot();
        route.params = { courseId: 1, faqId: 2 };
        let resolved: Faq | undefined;
        resolver.resolve(route).subscribe((faq) => (resolved = faq));
        const request = http.expectOne('/api/communication/courses/1/faqs/2/for-management');
        expect(request.request.method).toBe('GET');
        request.flush({ id: 2, questionTitle: 'Proposal', categories: ['{"category":"Exams","color":"#123456"}'], faqState: 'PROPOSED' } satisfies FaqResponse);
        expect(resolved).toBeInstanceOf(Faq);
        expect(resolved?.faqState).toBe(FaqState.PROPOSED);
        expect(resolved?.questionTitle).toBe('Proposal');
        expect(resolved?.categories).toEqual([new FaqCategory('Exams', '#123456')]);
    });

    it('creates an empty FAQ without requesting a detail for the new route', () => {
        const route = new ActivatedRouteSnapshot();
        route.params = {};
        resolver.resolve(route).subscribe((faq) => expect(faq).toEqual(new Faq()));
        http.expectNone(() => true);
    });
});

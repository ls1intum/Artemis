import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { FaqResolve } from './faq-resolve.service';
import { Faq, FaqState } from 'app/communication/shared/entities/faq.model';

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
        resolver.resolve(route).subscribe((faq) => expect(faq.faqState).toBe(FaqState.PROPOSED));
        http.expectOne('api/communication/courses/1/faqs/2/for-management').flush({ id: 2, faqState: FaqState.PROPOSED });
    });

    it('creates an empty FAQ without requesting a detail for the new route', () => {
        const route = new ActivatedRouteSnapshot();
        route.params = {};
        resolver.resolve(route).subscribe((faq) => expect(faq).toEqual(new Faq()));
        http.expectNone(() => true);
    });
});

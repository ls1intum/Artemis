import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { FaqService } from './faq.service';
import { loadCourseFaqCategories } from './faq.utils';
import { FaqState } from 'app/communication/shared/entities/faq.model';

describe('FAQ category audience', () => {
    let http: HttpTestingController;
    let service: FaqService;
    let alerts: AlertService;

    beforeEach(() => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), MockProvider(AlertService)] });
        http = TestBed.inject(HttpTestingController);
        service = TestBed.inject(FaqService);
        alerts = TestBed.inject(AlertService);
    });

    afterEach(() => http.verify());

    it.each([undefined, FaqState.PROPOSED, FaqState.REJECTED])('uses explicit management context for state %s', (state) => {
        loadCourseFaqCategories(1, alerts, service, state, 'management').subscribe((categories) => expect(categories).toEqual([{ category: 'staff', color: 'blue' }]));
        const request = http.expectOne((req) => req.url === 'api/communication/courses/1/faq-categories/for-management');
        expect(request.request.params.get('faqState')).toBe(state ?? null);
        request.flush([JSON.stringify({ category: 'staff', color: 'blue' })]);
    });

    it('keeps accepted student categories on the student URL', () => {
        loadCourseFaqCategories(1, alerts, service, FaqState.ACCEPTED).subscribe((categories) => expect(categories).toEqual([]));
        http.expectOne('api/communication/courses/1/faq-categories/ACCEPTED').flush([]);
    });

    it('keeps the unfiltered category catalogue URL', () => {
        loadCourseFaqCategories(1, alerts, service).subscribe((categories) => expect(categories).toEqual([]));
        http.expectOne('api/communication/courses/1/faq-categories').flush([]);
    });

    it('does not request categories without a course', () => {
        loadCourseFaqCategories(undefined, alerts, service).subscribe((categories) => expect(categories).toEqual([]));
        http.expectNone(() => true);
    });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

    it.each([undefined, FaqState.ACCEPTED, FaqState.PROPOSED, FaqState.REJECTED])('uses explicit management context for state %s', (state) => {
        let received: unknown;
        loadCourseFaqCategories(1, alerts, service, state, 'management').subscribe((categories) => (received = categories));
        const request = http.expectOne((req) => new URL(req.urlWithParams, 'http://localhost').pathname === '/api/communication/courses/1/faq-categories/for-management');
        const query = new URL(request.request.urlWithParams, 'http://localhost').searchParams;
        expect(query.get('faqState')).toBe(state ?? null);
        expect(query.has('faqState')).toBe(state !== undefined);
        request.flush([JSON.stringify({ category: 'staff', color: 'blue' })]);
        expect(received).toEqual([{ category: 'staff', color: 'blue' }]);
    });

    it('keeps accepted student categories on the student URL', () => {
        let received: unknown;
        loadCourseFaqCategories(1, alerts, service, FaqState.ACCEPTED).subscribe((categories) => (received = categories));
        http.expectOne('/api/communication/courses/1/faq-categories/ACCEPTED').flush([JSON.stringify({ category: 'Exams', color: '#123456' })]);
        expect(received).toEqual([{ category: 'Exams', color: '#123456' }]);
    });

    it('keeps the unfiltered category catalogue URL', () => {
        let received: unknown;
        loadCourseFaqCategories(1, alerts, service).subscribe((categories) => (received = categories));
        http.expectOne('/api/communication/courses/1/faq-categories').flush([]);
        expect(received).toEqual([]);
    });

    it('falls back to no categories and reports a failed request', () => {
        let received: unknown;
        const error = vi.spyOn(alerts, 'error');
        loadCourseFaqCategories(1, alerts, service).subscribe((categories) => (received = categories));
        http.expectOne('/api/communication/courses/1/faq-categories').flush(null, { status: 403, statusText: 'Forbidden' });
        expect(received).toEqual([]);
        expect(error).toHaveBeenCalledWith('error.http.403');
    });

    it('does not request categories without a course', () => {
        loadCourseFaqCategories(undefined, alerts, service).subscribe((categories) => expect(categories).toEqual([]));
        http.expectNone(() => true);
    });
});

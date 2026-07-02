import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import dayjs from 'dayjs/esm';

import { DeimosService } from 'app/programming/shared/services/deimos.service';

describe('DeimosService', () => {
    setupTestBed({ zoneless: true });

    let httpMock: HttpTestingController;
    let deimosService: DeimosService;

    const from = dayjs('2026-01-01T10:00:00Z');
    const to = dayjs('2026-01-08T18:00:00Z');
    const triggerResponse = { runId: 'run-abc', status: 'ACCEPTED' };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), DeimosService],
        });
        httpMock = TestBed.inject(HttpTestingController);
        deimosService = TestBed.inject(DeimosService);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should trigger course batch with ISO date range', () => {
        let responseBody: { runId: string; status: string } | undefined;
        deimosService.triggerCourseBatch(42, from, to).subscribe((response) => {
            responseBody = response;
        });

        const request = httpMock.expectOne('api/deimos/courses/42/analysis-runs');
        expect(request.request.method).toBe('POST');
        expect(request.request.body).toEqual({ from: from.toISOString(), to: to.toISOString() });
        request.flush(triggerResponse);

        expect(responseBody).toEqual(triggerResponse);
    });

    it('should trigger exercise batch with ISO date range', () => {
        let responseBody: { runId: string; status: string } | undefined;
        deimosService.triggerExerciseBatch(99, from, to).subscribe((response) => {
            responseBody = response;
        });

        const request = httpMock.expectOne('api/deimos/programming-exercises/99/analysis-runs');
        expect(request.request.method).toBe('POST');
        expect(request.request.body).toEqual({ from: from.toISOString(), to: to.toISOString() });
        request.flush(triggerResponse);

        expect(responseBody).toEqual(triggerResponse);
    });
});

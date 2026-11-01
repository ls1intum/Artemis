import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ErrorAnalysisService } from './error-analysis.service';
import { ErrorGroup } from './error-analysis.model';

describe('ErrorAnalysisService', () => {
    let service: ErrorAnalysisService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(ErrorAnalysisService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('should request the groups of the selected span, location and severity', () => {
        const groups: ErrorGroup[] = [
            {
                location: 'SERVER',
                severity: 'ERROR',
                message: 'ExerciseService: Could not find exercise <n>',
                count: 4,
                firstSeen: '2026-11-01T10:00:00Z',
                lastSeen: '2026-11-01T12:00:00Z',
            },
        ];
        let result: ErrorGroup[] | undefined;

        service.getGroups('WEEK', 'SERVER', 'CRITICAL').subscribe((response) => (result = response));

        const request = httpMock.expectOne((req) => req.url === 'api/core/admin/error-analysis');
        expect(request.request.method).toBe('GET');
        expect(request.request.params.get('span')).toBe('WEEK');
        expect(request.request.params.get('location')).toBe('SERVER');
        expect(request.request.params.get('minSeverity')).toBe('CRITICAL');
        request.flush(groups);
        expect(result).toEqual(groups);
    });

    it('should leave the location out to ask for the server and the clients', () => {
        service.getGroups('DAY', 'ALL', 'ERROR').subscribe();

        const request = httpMock.expectOne((req) => req.url === 'api/core/admin/error-analysis');
        expect(request.request.params.has('location')).toBe(false);
        expect(request.request.params.get('span')).toBe('DAY');
        expect(request.request.params.get('minSeverity')).toBe('ERROR');
        request.flush([]);
    });
});

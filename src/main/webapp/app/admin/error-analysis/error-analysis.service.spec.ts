import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ErrorAnalysisService } from './error-analysis.service';
import { ErrorAnalysisPage, ErrorAnalysisQuery } from './error-analysis.model';

describe('ErrorAnalysisService', () => {
    let service: ErrorAnalysisService;
    let httpMock: HttpTestingController;

    const query: ErrorAnalysisQuery = { span: 'RELEASE', location: 'ALL', minSeverity: 'ERROR', sortField: 'COUNT', ascending: false, page: 0, pageSize: 25 };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(ErrorAnalysisService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('should request the page of the selected span, location, severity and sort order', () => {
        const page: ErrorAnalysisPage = {
            groups: [
                {
                    location: 'SERVER',
                    severity: 'ERROR',
                    message: 'ExerciseService: Could not find exercise <n>',
                    count: 4,
                    firstSeen: '2026-11-01T10:00:00Z',
                    lastSeen: '2026-11-01T12:00:00Z',
                },
            ],
            totalGroups: 31,
            totalErrors: 120,
        };
        let result: ErrorAnalysisPage | undefined;

        service
            .getGroups({ span: 'WEEK', location: 'SERVER', minSeverity: 'CRITICAL', sortField: 'LAST_SEEN', ascending: true, page: 2, pageSize: 50 })
            .subscribe((response) => (result = response));

        const request = httpMock.expectOne((req) => req.url === 'api/core/admin/error-analysis');
        expect(request.request.method).toBe('GET');
        expect(request.request.params.get('span')).toBe('WEEK');
        expect(request.request.params.get('location')).toBe('SERVER');
        expect(request.request.params.get('minSeverity')).toBe('CRITICAL');
        expect(request.request.params.get('sortBy')).toBe('LAST_SEEN');
        expect(request.request.params.get('direction')).toBe('ASC');
        expect(request.request.params.get('page')).toBe('2');
        expect(request.request.params.get('size')).toBe('50');
        request.flush(page);
        expect(result).toEqual(page);
    });

    it('should ask for the release that is running, the most frequent first and the first page by default', () => {
        service.getGroups(query).subscribe();

        const request = httpMock.expectOne((req) => req.url === 'api/core/admin/error-analysis');
        expect(request.request.params.get('span')).toBe('RELEASE');
        expect(request.request.params.get('sortBy')).toBe('COUNT');
        expect(request.request.params.get('direction')).toBe('DESC');
        expect(request.request.params.get('page')).toBe('0');
        expect(request.request.params.get('size')).toBe('25');
        request.flush({ groups: [], totalGroups: 0, totalErrors: 0 });
    });

    it('should leave the location out to ask for the server and the clients', () => {
        service.getGroups(query).subscribe();

        const request = httpMock.expectOne((req) => req.url === 'api/core/admin/error-analysis');
        expect(request.request.params.has('location')).toBe(false);
        request.flush({ groups: [], totalGroups: 0, totalErrors: 0 });
    });

    it('should accept an answer without groups, which the server leaves out when there are none', () => {
        let result: ErrorAnalysisPage | undefined;

        service.getGroups(query).subscribe((response) => (result = response));

        httpMock.expectOne((req) => req.url === 'api/core/admin/error-analysis').flush({});
        expect(result).toEqual({ groups: [], totalGroups: 0, totalErrors: 0 });
    });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { ProgrammingAssessmentRepoExportService, RepositoryExportOptions } from './programming-assessment-repo-export.service';

describe('Programming repository export operations', () => {
    let service: ProgrammingAssessmentRepoExportService;
    let http: HttpTestingController;
    const options: RepositoryExportOptions = {
        exportAllParticipants: false,
        anonymizeRepository: false,
        filterLateSubmissions: true,
        filterLateSubmissionsDate: dayjs('2026-01-10'),
        excludePracticeSubmissions: true,
        addParticipantName: true,
        combineStudentCommits: false,
        normalizeCodeStyle: false,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(ProgrammingAssessmentRepoExportService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('preserves selected IDs and all data options on the identified instructor operation', () => {
        service.exportIdentifiedReposByParticipations(1, [2, 3], options).subscribe();
        const req = http.expectOne({ method: 'POST', url: 'api/programming/programming-exercises/1/export-repos-by-participation-ids/2,3/for-instructor' });
        expect(req.request.body).toEqual(options);
        req.flush(new Blob());
    });

    it('keeps the tutor-purpose selected-ID route and forwards the explicit anonymous options', () => {
        const anonymous = { ...options, anonymizeRepository: true };
        service.exportReposByParticipations(1, [2, 3], anonymous).subscribe();
        const req = http.expectOne({ method: 'POST', url: 'api/programming/programming-exercises/1/export-repos-by-participation-ids/2,3' });
        expect(req.request.body).toEqual(anonymous);
        req.flush(new Blob());
    });

    it('reuses the instructor identifiers operation for ALL with all options intact', () => {
        const all = { ...options, exportAllParticipants: true };
        service.exportReposByParticipantIdentifiers(1, ['ALL'], all).subscribe();
        const req = http.expectOne({ method: 'POST', url: 'api/programming/programming-exercises/1/export-repos-by-participant-identifiers/ALL' });
        expect(req.request.body).toEqual(all);
        req.flush(new Blob());
    });
});

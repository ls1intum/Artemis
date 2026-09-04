import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AssessmentUploadResult, AssessmentUploadService } from 'app/assessment/manage/services/assessment-upload.service';

describe('AssessmentUploadService', () => {
    let service: AssessmentUploadService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(AssessmentUploadService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('should POST the zip file as multipart form data to the exercise-scoped endpoint', () => {
        const file = new File(['dummy'], 'assessments.zip', { type: 'application/zip' });
        const expected: AssessmentUploadResult = { numberOfCreatedAssessments: 1, createdStudentIdentifiers: ['42-ab12cde'] };
        let actual: AssessmentUploadResult | undefined;

        service.uploadManualAssessments(7, file).subscribe((response) => (actual = response.body ?? undefined));

        const request = httpMock.expectOne('api/assessment/exercises/7/manual-assessments');
        expect(request.request.method).toBe('POST');
        expect(request.request.body).toBeInstanceOf(FormData);
        expect((request.request.body as FormData).get('file')).toBe(file);

        request.flush(expected);
        expect(actual).toEqual(expected);
    });

    it('should GET the template zip as a blob from the exercise-scoped endpoint', () => {
        const expected = new Blob(['template']);
        let actual: Blob | undefined;

        service.downloadTemplate(7).subscribe((response) => (actual = response.body ?? undefined));

        const request = httpMock.expectOne('api/assessment/exercises/7/manual-assessments/template');
        expect(request.request.method).toBe('GET');
        expect(request.request.responseType).toBe('blob');

        request.flush(expected);
        expect(actual).toEqual(expected);
    });
});

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { LectureTranscriptionDTO, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';

describe('LectureTranscriptionService', () => {
    setupTestBed({ zoneless: true });

    let service: LectureTranscriptionService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(LectureTranscriptionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get transcriptions', () => {
        const dto: LectureTranscriptionDTO = { lectureUnitId: 1, language: 'en', segments: [] };
        let result: LectureTranscriptionDTO | undefined;
        service.getTranscription(1).subscribe((r) => (result = r));
        const req = httpMock.expectOne(`api/lecture/lecture-unit/1/transcript`);
        req.flush(dto, { status: 200, statusText: 'OK' });
        expect(result).toEqual(dto);
    });

    it('should return undefined on get transcription error', () => {
        let result: LectureTranscriptionDTO | undefined;
        service.getTranscription(1).subscribe((r) => (result = r));
        const req = httpMock.expectOne(`api/lecture/lecture-unit/1/transcript`);
        req.flush('error', { status: 404, statusText: 'Not Found' });
        expect(result).toBeUndefined();
    });

    it('should get transcription status', () => {
        let result: TranscriptionStatus | undefined;
        service.getTranscriptionStatus(1).subscribe((r) => (result = r));
        const req = httpMock.expectOne(`api/lecture/lecture-unit/1/transcript/status`);
        req.flush('COMPLETED', { status: 200, statusText: 'OK' });
        expect(result).toBe('COMPLETED');
    });

    it('should return undefined on get transcription status error', () => {
        let result: TranscriptionStatus | undefined;
        service.getTranscriptionStatus(1).subscribe((r) => (result = r));
        const req = httpMock.expectOne(`api/lecture/lecture-unit/1/transcript/status`);
        req.flush('error', { status: 500, statusText: 'Server Error' });
        expect(result).toBeUndefined();
    });

    it('should return undefined when transcription status response body is empty', () => {
        let result: TranscriptionStatus | undefined;
        service.getTranscriptionStatus(1).subscribe((r) => (result = r));
        const req = httpMock.expectOne(`api/lecture/lecture-unit/1/transcript/status`);
        req.flush('', { status: 200, statusText: 'OK' });
        expect(result).toBeUndefined();
    });

    it('should return undefined when transcription response body is null', () => {
        let result: LectureTranscriptionDTO | undefined;
        service.getTranscription(1).subscribe((r) => (result = r));
        const req = httpMock.expectOne(`api/lecture/lecture-unit/1/transcript`);
        req.flush(null, { status: 200, statusText: 'OK' });
        expect(result).toBeUndefined();
    });
});

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { LectureTranscriptionDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';

describe('LectureTranscriptionService', () => {
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

    it('should ingest transcription', () => {
        const courseId = 1;
        const lectureId = 1;
        const lectureUnitId = 1;
        service.ingestTranscription(courseId, lectureId, lectureUnitId).subscribe(() => {});

        const req = httpMock.expectOne({ method: 'POST', url: `api/lecture/lectures/${lectureId}/lecture-units/${lectureUnitId}/ingest` });
        req.flush({});
    });

    it('should return false on ingest transcription error', () => {
        const courseId = 1;
        const lectureId = 1;
        const lectureUnitId = 1;
        let result: boolean | undefined;
        service.ingestTranscription(courseId, lectureId, lectureUnitId).subscribe((r) => (result = r));
        const req = httpMock.expectOne(`api/lecture/lectures/${lectureId}/lecture-units/${lectureUnitId}/ingest`);
        req.flush('error', { status: 500, statusText: 'Server Error' });
        expect(result).toBeFalse();
    });

    it('should create transcription', () => {
        const transcription = { lectureUnitId: 1, language: 'en', segments: [] };
        const lectureId = 1;
        const lectureUnitId = 1;
        service.createTranscription(lectureId, lectureUnitId, transcription).subscribe(() => {});

        const req = httpMock.expectOne({ method: 'POST', url: `api/lecture/${lectureId}/lecture-unit/${lectureUnitId}/transcription` });

        expect(req.request.body).toBe(transcription);
        req.flush({});
    });

    it('should return false on create transcription error', () => {
        const transcription = { lectureUnitId: 1, language: 'en', segments: [] };
        let result: boolean | undefined;
        service.createTranscription(1, 1, transcription).subscribe((r) => (result = r));
        const req = httpMock.expectOne(`api/lecture/1/lecture-unit/1/transcription`);
        req.flush('error', { status: 500, statusText: 'Server Error' });
        expect(result).toBeFalse();
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
});

import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LectureTranscriptionService } from 'app/admin/lecture-transcription-ingestion/lecture-transcription.service';

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

    it('should send PUT request to ingest transcription', () => {
        const courseId = 1;
        const lectureId = 1;
        const lectureUnitId = 1;
        service.ingestTranscription(courseId, lectureId, lectureUnitId).subscribe(() => {});

        const req = httpMock.expectOne({ method: 'PUT', url: `api/lecture/courses/${courseId}/lectures/${lectureId}/lecture-unit/${lectureUnitId}/ingest-transcription` });
        req.flush({});
    });

    it('should send POST request to create transcription', () => {
        const transcription = { transcription: [] };
        const lectureId = 1;
        const lectureUnitId = 1;
        service.createTranscription(lectureId, lectureUnitId, transcription).subscribe(() => {});

        const req = httpMock.expectOne({ method: 'POST', url: `api/lecture/${lectureId}/lecture-unit/${lectureUnitId}/transcriptions` });

        expect(req.request.body).toBe(transcription);
        req.flush({});
    });
});

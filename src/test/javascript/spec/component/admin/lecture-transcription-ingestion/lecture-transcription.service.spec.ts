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
        service.ingestTranscription(courseId, lectureId).subscribe(() => {});

        const req = httpMock.expectOne({ method: 'PUT', url: `api/courses/${courseId}/ingest-transcription?lectureId=${lectureId}` });
        req.flush({});
    });

    it('should send POST request to create transcription', () => {
        const transcription = { transcription: [] };
        service.createTranscription(transcription).subscribe(() => {});

        const req = httpMock.expectOne({ method: 'POST', url: `api/transcription` });

        expect(req.request.body).toBe(transcription);
        req.flush({});
    });
});

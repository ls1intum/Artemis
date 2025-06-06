import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import dayjs from 'dayjs/esm';
import { SubmissionVersionService } from 'app/exercise/submission-version/submission-version.service';
import { provideHttpClient } from '@angular/common/http';

describe('SubmissionVersion Service', () => {
    let service: SubmissionVersionService;
    let httpMock: HttpTestingController;
    const submission = new TextSubmission();

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        service = TestBed.inject(SubmissionVersionService);
        httpMock = TestBed.inject(HttpTestingController);
    });
    it('should get submission versions for submission', fakeAsync(() => {
        const submissionId = 1;
        const submissionVersion = {
            id: 1,
            submission: submission,
            content: 'text',
            createdDate: dayjs(),
        };
        const expected = [submissionVersion];
        service.findAllSubmissionVersionsOfSubmission(submissionId).subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ url: `api/exercise/submissions/${submissionId}/versions`, method: 'GET' });
        req.flush(expected);
        tick();
    }));
});

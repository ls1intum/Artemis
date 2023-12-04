import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TextSubmission } from 'app/entities/text-submission.model';
import dayjs from 'dayjs/esm';
import { SubmissionVersionService } from 'app/exercises/shared/submission-version/submission-version.service';

describe('SubmissionVersion Service', () => {
    let service: SubmissionVersionService;
    let httpMock: HttpTestingController;
    let submission: TextSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
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
        const req = httpMock.expectOne({ url: `api/submissions/${submissionId}/versions`, method: 'GET' });
        req.flush(expected);
        tick();
    }));
});

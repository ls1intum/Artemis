import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';

describe('FileUploadSubmission Service', () => {
    let injector: TestBed;
    let service: FileUploadSubmissionService;
    let httpMock: HttpTestingController;
    let elemDefault: FileUploadSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(FileUploadSubmissionService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new FileUploadSubmission();
    });

    it('should find an element', async () => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .get(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should create/update a FileUploadSubmission', async () => {
        const returnedFromService = Object.assign(
            {
                id: 0,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .update(new FileUploadSubmission(), 1, new Blob())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should return a list of FileUploadSubmission for an exercise', async () => {
        const returnedFromService = Object.assign(
            {
                filePath: 'BBBBBB',
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .getFileUploadSubmissionsForExerciseByCorrectionRound(1, { submittedOnly: true })
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify([returnedFromService]));
        httpMock.verify();
    });

    it('should return next submission with no assessment', async () => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getFileUploadSubmissionForExerciseForCorrectionRoundWithoutAssessment(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should return data from participation id', async () => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getDataForFileUploadEditor(42)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    afterEach(() => {
        httpMock.verify();
    });
});

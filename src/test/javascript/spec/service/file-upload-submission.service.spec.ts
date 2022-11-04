import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';

describe('FileUploadSubmission Service', () => {
    let service: FileUploadSubmissionService;
    let httpMock: HttpTestingController;
    let elemDefault: FileUploadSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(FileUploadSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new FileUploadSubmission();
    });

    it('should find an element', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .get(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should create/update a FileUploadSubmission', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                id: 0,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .update(new FileUploadSubmission(), 1, new File([''], ''))
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should return a list of FileUploadSubmission for an exercise', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                filePath: 'BBBBBB',
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .getSubmissions(1, { submittedOnly: true })
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([returnedFromService]);
        httpMock.verify();
        tick();
    }));

    it('should return next submission with no assessment', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getSubmissionWithoutAssessment(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(elemDefault));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should return data from participation id', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getDataForFileUploadEditor(42)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject(elemDefault));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});

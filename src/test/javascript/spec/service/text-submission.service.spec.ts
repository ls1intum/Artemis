import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { TextSubmission } from 'app/entities/text-submission.model';

describe('TextSubmission Service', () => {
    let injector: TestBed;
    let service: TextSubmissionService;
    let httpMock: HttpTestingController;
    let elemDefault: TextSubmission;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(TextSubmissionService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new TextSubmission();
    });

    describe('Service methods', async () => {
        it('should create a TextSubmission', async () => {
            const returnedFromService = Object.assign(
                {
                    id: 0,
                },
                elemDefault,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .create(new TextSubmission(null))
                .pipe(take(1))
                .subscribe((resp: any) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should update a TextSubmission', async () => {
            const returnedFromService = Object.assign(
                {
                    text: 'BBBBBB',
                },
                elemDefault,
            );

            const expected = Object.assign({}, returnedFromService);
            service
                .update(expected)
                .pipe(take(1))
                .subscribe((resp: any) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(JSON.stringify(returnedFromService));
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});

import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';

describe('ModelingSubmission Service', () => {
    let injector: TestBed;
    let service: ModelingSubmissionService;
    let httpMock: HttpTestingController;
    let elemDefault: ModelingSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(ModelingSubmissionService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new ModelingSubmission();
    });

    describe('Service methods', async () => {
        it('should find an element', async () => {
            const returnedFromService = Object.assign({}, elemDefault);
            service
                .getSubmission(123)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should create a ModelingSubmission', async () => {
            const returnedFromService = Object.assign(
                {
                    id: 0,
                },
                elemDefault,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .create(new ModelingSubmission(), 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should update a ModelingSubmission', async () => {
            const returnedFromService = Object.assign(
                {
                    model: 'BBBBBB',
                    explanationText: 'BBBBBB',
                },
                elemDefault,
            );

            const expected = Object.assign({}, returnedFromService);
            service
                .update(expected, 1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(JSON.stringify(returnedFromService));
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});

import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { HttpRequest } from '@angular/common/http';

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

    it('should getModelingSubmissionsForExerciseByCorrectionRound without correction round', async () => {
        const returnedFromService = Object.assign(
            {
                id: 5,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        const requestOption = { test: 'Test' };
        service
            .getModelingSubmissionsForExerciseByCorrectionRound(5, requestOption)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/exercises/${5}/modeling-submissions?test=Test` });
        expect(req.request.params.get('test')).toBe('Test');
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should getModelingSubmissionsForExerciseByCorrectionRound without correction round', async () => {
        const returnedFromService = Object.assign(
            {
                id: 5,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        const requestOption = { test: 'Test' };
        const correctionRound = 6;
        service
            .getModelingSubmissionsForExerciseByCorrectionRound(5, requestOption, correctionRound)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/exercises/${5}/modeling-submissions?test=Test&correction-round=${correctionRound}` });
        expect(req.request.params.get('test')).toBe('Test');
        expect(req.request.params.get('correction-round')).toBe(`${correctionRound}`);
        req.flush(JSON.stringify(returnedFromService));
    });

    afterEach(() => {
        httpMock.verify();
    });
});

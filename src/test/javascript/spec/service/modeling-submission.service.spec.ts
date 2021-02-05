import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { getTestBed, TestBed } from '@angular/core/testing';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { take } from 'rxjs/operators';

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

    const getDefaultValues = (updateValues = {}) => {
        const exerciseId = 5;
        const returnedFromService = { ...elemDefault, id: exerciseId, ...updateValues };
        const expected = { ...returnedFromService };
        const requestOption = { test: 'Test' };
        const correctionRound = 7;
        const participationId = 4;
        return { exerciseId, returnedFromService, expected, requestOption, correctionRound, participationId };
    };

    it('should find an element', async () => {
        const { returnedFromService, correctionRound } = getDefaultValues();
        service
            .getSubmission(123, correctionRound)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.get('correction-round')).toBe(`${correctionRound}`);
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should create a ModelingSubmission', async () => {
        const { returnedFromService, expected } = getDefaultValues();
        service
            .create(new ModelingSubmission(), 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should update a ModelingSubmission', async () => {
        const { returnedFromService, expected } = getDefaultValues({ model: 'BBBBBB', explanationText: 'BBBBBB' });
        service
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should getModelingSubmissionsForExerciseByCorrectionRound without correction round', async () => {
        const { exerciseId, returnedFromService, expected, requestOption } = getDefaultValues();
        service
            .getModelingSubmissionsForExerciseByCorrectionRound(exerciseId, requestOption)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/exercises/${exerciseId}/modeling-submissions?test=Test` });
        expect(req.request.params.get('test')).toBe('Test');
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should getModelingSubmissionsForExerciseByCorrectionRound without correction round', async () => {
        const { exerciseId, returnedFromService, expected, requestOption, correctionRound } = getDefaultValues();
        service
            .getModelingSubmissionsForExerciseByCorrectionRound(5, requestOption, correctionRound)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/exercises/${exerciseId}/modeling-submissions?test=Test&correction-round=${correctionRound}` });
        expect(req.request.params.get('test')).toBe('Test');
        expect(req.request.params.get('correction-round')).toBe(`${correctionRound}`);
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment', () => {
        const { exerciseId, returnedFromService, expected, correctionRound } = getDefaultValues();
        service
            .getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment(exerciseId, true, correctionRound)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'GET', url: `api/exercises/${exerciseId}/modeling-submission-without-assessment?correction-round=${correctionRound}&lock=true` });
        expect(req.request.params.get('lock')).toBe('true');
        expect(req.request.params.get('correction-round')).toBe(`${correctionRound}`);
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should getLatestSubmissionForModelingEditor', () => {
        const { returnedFromService, expected, participationId } = getDefaultValues();
        service
            .getLatestSubmissionForModelingEditor(participationId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'GET', url: `api/participations/${participationId}/latest-modeling-submission` });
        req.flush(JSON.stringify(returnedFromService));
    });

    afterEach(() => {
        httpMock.verify();
    });
});

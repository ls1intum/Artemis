import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { take } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';

describe('ModelingSubmission Service', () => {
    let service: ModelingSubmissionService;
    let httpMock: HttpTestingController;
    let elemDefault: ModelingSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(ModelingSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new ModelingSubmission();
        elemDefault.id = 5;
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

    it('should find an element', fakeAsync(() => {
        const { returnedFromService, correctionRound } = getDefaultValues();
        service
            .getSubmission(123, correctionRound)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual({ ...elemDefault }));

        const req = httpMock.expectOne({ method: 'GET', url: `api/modeling/modeling-submissions/123?correction-round=7` });
        expect(req.request.params.get('correction-round')).toBe(`${correctionRound}`);
        req.flush(returnedFromService);
        tick();
    }));

    it('should create a ModelingSubmission', fakeAsync(() => {
        const { returnedFromService, expected } = getDefaultValues();
        service
            .create(new ModelingSubmission(), 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update a ModelingSubmission', fakeAsync(() => {
        const { returnedFromService, expected } = getDefaultValues({ model: 'BBBBBB', explanationText: 'BBBBBB' });
        service
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get submissions without correction round', fakeAsync(() => {
        const { exerciseId, returnedFromService, requestOption } = getDefaultValues();
        service
            .getSubmissions(exerciseId, requestOption)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: [] }));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/exercises/${exerciseId}/modeling-submissions?test=Test` });
        expect(req.request.params.get('test')).toBe('Test');
        req.flush(returnedFromService);
        tick();
    }));

    it('should get submissions with correction round', fakeAsync(() => {
        const { exerciseId, returnedFromService, requestOption, correctionRound } = getDefaultValues();
        service
            .getSubmissions(5, requestOption, correctionRound)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: [] }));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/exercises/${exerciseId}/modeling-submissions?test=Test&correction-round=${correctionRound}` });
        expect(req.request.params.get('test')).toBe('Test');
        expect(req.request.params.get('correction-round')).toBe(`${correctionRound}`);
        req.flush(returnedFromService);
        tick();
    }));

    it('should getSubmissionWithoutAssessment', fakeAsync(() => {
        const { exerciseId, returnedFromService, correctionRound } = getDefaultValues();
        service
            .getSubmissionWithoutAssessment(exerciseId, true, correctionRound)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toStrictEqual({ ...elemDefault }));
        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/modeling/exercises/${exerciseId}/modeling-submission-without-assessment?correction-round=${correctionRound}&lock=true`,
        });
        expect(req.request.params.get('lock')).toBe('true');
        expect(req.request.params.get('correction-round')).toBe(`${correctionRound}`);
        req.flush(returnedFromService);
        tick();
    }));

    it('should getLatestSubmissionForModelingEditor', fakeAsync(() => {
        const { returnedFromService, participationId } = getDefaultValues();
        service
            .getLatestSubmissionForModelingEditor(participationId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ ...elemDefault }));
        const req = httpMock.expectOne({ method: 'GET', url: `api/modeling/participations/${participationId}/latest-modeling-submission` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get submissions with results for participation', fakeAsync(() => {
        const { participationId, returnedFromService } = getDefaultValues();
        const submissions = [returnedFromService];

        service
            .getSubmissionsWithResultsForParticipation(participationId)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp).toEqual([returnedFromService]);
            });

        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/modeling/participations/${participationId}/submissions-with-results`,
        });
        req.flush(submissions);
        tick();
    }));

    it('should get submission without lock', fakeAsync(() => {
        const { returnedFromService } = getDefaultValues();

        service
            .getSubmissionWithoutLock(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual({ ...elemDefault }));

        const req = httpMock.expectOne((request) => request.method === 'GET' && request.urlWithParams === 'api/modeling/modeling-submissions/123?withoutResults=true');
        expect(req.request.params.get('withoutResults')).toBe('true');
        req.flush(returnedFromService);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});

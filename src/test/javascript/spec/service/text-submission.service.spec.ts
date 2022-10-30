import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';

describe('TextSubmission Service', () => {
    let service: TextSubmissionService;
    let httpMock: HttpTestingController;
    let elemDefault: TextSubmission;

    const mockResponse = {
        submissionExerciseType: 'text',
        id: 1,
        submitted: true,
        type: 'MANUAL',
        participation: {
            type: 'student',
            id: 1,
            initializationState: 'FINISHED',
            initializationDate: '2020-07-07T14:34:18.067248+02:00',
            exercise: {
                type: 'text',
                id: 1,
            },
            participantIdentifier: 'ga27der',
            participantName: 'Jonas Petry',
        },
        result: {
            id: 5,
            assessmentType: 'MANUAL',
        },
        submissionDate: '2020-07-07T14:34:25.194518+02:00',
        durationInMinutes: 0,
        text: 'Test\n\nTest\n\nTest',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(TextSubmissionService);
                httpMock = TestBed.inject(HttpTestingController);

                elemDefault = new TextSubmission();
            });
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create a TextSubmission', fakeAsync(() => {
        const returnedFromService = {
            id: 1,
            ...elemDefault,
        };

        const expected = { ...returnedFromService };
        service
            .create(new TextSubmission(), 1)
            .pipe(take(1))
            .subscribe((resp: any) => {
                expect(resp.body).toEqual(expected);
            });
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update a TextSubmission', fakeAsync(() => {
        const returnedFromService = {
            text: 'BBBBBB',
            ...elemDefault,
        };

        const expected = { ...returnedFromService };
        service
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp: any) => expect(resp.body).toEqual(expected));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should not parse jwt from header', fakeAsync(() => {
        service.getSubmissionWithoutAssessment(1).subscribe((textSubmission) => {
            expect(textSubmission.atheneTextAssessmentTrackingToken).toBeUndefined();
        });

        const mockRequest = httpMock.expectOne({ method: 'GET' });
        mockRequest.flush(mockResponse);
        tick();
    }));

    it('should parse jwt from header', fakeAsync(() => {
        service.getSubmissionWithoutAssessment(1).subscribe((textSubmission) => {
            expect(textSubmission.atheneTextAssessmentTrackingToken).toBe('12345');
        });

        const mockRequest = httpMock.expectOne({ method: 'GET' });
        mockRequest.flush(mockResponse, { headers: { 'x-athene-tracking-authorization': '12345' } });
        tick();
    }));

    it('should get textSubmission for exercise', fakeAsync(() => {
        const exerciseId = 1;
        elemDefault = new TextSubmission();
        elemDefault.latestResult = undefined;
        elemDefault.participation = undefined;
        const returnedFromService = [elemDefault];
        const expected = returnedFromService;
        let response: any;
        service
            .getSubmissions(exerciseId, {})
            .pipe(take(1))
            .subscribe((resp) => (response = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(response.body).toEqual(expected);
        tick();
    }));

    it('should get textSubmission', fakeAsync(() => {
        const exerciseId = 1;
        elemDefault = new TextSubmission();
        const returnedFromService = { body: elemDefault };
        const expected = returnedFromService.body;
        let response: any;
        service
            .getTextSubmission(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => (response = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(response.body).toEqual(expected);
        tick();
    }));
});

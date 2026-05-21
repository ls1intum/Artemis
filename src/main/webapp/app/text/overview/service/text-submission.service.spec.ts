/**
 * Test suite for TextSubmissionService.
 * Tests CRUD operations and HTTP interactions for text submissions.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { provideHttpClient } from '@angular/common/http';

describe('TextSubmission Service', () => {
    setupTestBed({ zoneless: true });
    let service: TextSubmissionService;
    let httpMock: HttpTestingController;
    let elemDefault: TextSubmission;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        }).compileComponents();
        service = TestBed.inject(TextSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new TextSubmission();
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create a TextSubmission', () => {
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
    });

    it('should update a TextSubmission', () => {
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
    });

    it('should get textSubmission for exercise', () => {
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
    });

    it('should get textSubmission', () => {
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
    });

    it('should get submission without assessment', () => {
        const exerciseId = 1;
        elemDefault = new TextSubmission();
        elemDefault.participation = new StudentParticipation();
        const returnedFromService = elemDefault;
        const expected = returnedFromService;

        service
            .getSubmissionWithoutAssessment(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp).toEqual(expected);
            });

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });
});

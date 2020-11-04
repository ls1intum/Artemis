import { EntityArrayResponseType, SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { getTestBed, TestBed } from '@angular/core/testing';
import { expect } from '../helpers/jest.fix';
import { map, take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import moment = require('moment');

describe('Submission Service', () => {
    let injector: TestBed;
    let service: SubmissionService;
    let httpMock: HttpTestingController;
    let elemDefault: Submission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        injector = getTestBed();
        service = injector.get(SubmissionService);
        httpMock = injector.get(HttpTestingController);

        // @ts-ignore
        elemDefault = new Submission(SubmissionExerciseType.TEXT);
    });

    it('should delete an existing submission', async () => {
        service.delete(187).subscribe((resp) => expect(resp.ok));
        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
    });

    it('should find all submissions of a given participation', async () => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .findAllSubmissionsOfParticipation(187)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toMatchObject({ body: elemDefault }));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify([returnedFromService]));
        // httpMock.verify();
    });

    it('should find all submissions of a given participation, 2.0', async () => {
        const returnedFromService = Object.assign({}, elemDefault);
        service.findAllSubmissionsOfParticipation(187).subscribe((body) => expect(body).toBe({ body: [elemDefault] }));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify([returnedFromService]));
        // httpMock.verify();
    });

    it('should get test run submission for a given exercise', async () => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getTestRunSubmissionsForExercise(123)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toMatchObject({ body: elemDefault }));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
    });

    afterEach(() => {
        httpMock.verify();
    });
});

import * as moment from 'moment';
import * as chai from 'chai';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { getTestBed, TestBed } from '@angular/core/testing';
import { Result } from 'app/entities/result.model';
import { expect } from '../helpers/jest.fix';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

describe('Submission Service', () => {
    let injector: TestBed;
    let service: SubmissionService;
    let httpMock: HttpTestingController;

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
    });

    it('should delete an existing submission', async () => {
        service.delete(187).subscribe((resp) => expect(resp.ok));
        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
    });

    it('should find all submissions of a given participation', async () => {
        service.findAllSubmissionsOfParticipation(187).subscribe((resp) => expect(resp.ok));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush({ status: 200 });
    });

    it('should get test run submission for a given exercise', async () => {
        service.getTestRunSubmissionsForExercise(123).subscribe((resp) => expect(resp.ok));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush({ status: 200 });
    });

    afterEach(() => {
        httpMock.verify();
    });
});

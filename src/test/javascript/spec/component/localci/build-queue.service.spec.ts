import { TestBed } from '@angular/core/testing';

import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { BuildJob } from 'app/entities/build-job.model';
import dayjs from 'dayjs/esm';

describe('BuildQueueService', () => {
    let service: BuildQueueService;
    let httpMock: HttpTestingController;
    let elemDefault: BuildJob;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(BuildQueueService);
        httpMock = TestBed.inject(HttpTestingController);
        elemDefault = new BuildJob();
        elemDefault.id = 1;
        elemDefault.name = 'test';
        elemDefault.participationId = 1;
        elemDefault.repositoryTypeOrUserName = 'test';
        elemDefault.commitHash = 'test';
        elemDefault.submissionDate = dayjs('2023-01-02');
        elemDefault.retryCount = 1;
        elemDefault.buildStartDate = 0;
        elemDefault.priority = 1;
        elemDefault.courseId = 1;
        elemDefault.isPushToTestRepository = false;
    });

    it('should return build job for course', () => {
        const courseId = 1;
        const expectedResponse = [elemDefault]; // Expecting an array

        service.getQueuedBuildJobsByCourseId(courseId).subscribe((data) => {
            expect(data).toEqual(expectedResponse); // Check if the response matches expected
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/queued/${courseId}`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse); // Flush an array of elements
    });

    it('should return running build jobs for a specific course', () => {
        const courseId = 1;
        const expectedResponse = [elemDefault]; // Assuming this is your expected response

        service.getRunningBuildJobsByCourseId(courseId).subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/running/${courseId}`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should return all queued build jobs', () => {
        const expectedResponse = [elemDefault]; // Assuming this is your expected response

        service.getQueuedBuildJobs().subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/queued`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should return all running build jobs', () => {
        const expectedResponse = [elemDefault]; // Assuming this is your expected response

        service.getRunningBuildJobs().subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/running`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    afterEach(() => {
        httpMock.verify(); // Verify that there are no outstanding requests.
    });
});

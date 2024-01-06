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
    let elem1: BuildJob;

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
        elem1 = new BuildJob();
        elem1.id = 1;
        elem1.name = 'test1';
        elem1.participationId = 1;
        elem1.repositoryTypeOrUserName = 'test1';
        elem1.commitHash = 'test1';
        elem1.submissionDate = dayjs('2023-01-02');
        elem1.retryCount = 1;
        elem1.buildStartDate = dayjs('2023-01-02');
        elem1.priority = 1;
        elem1.courseId = 1;
        elem1.isPushToTestRepository = false;
    });

    it('should return build job for course', () => {
        const courseId = 1;
        const expectedResponse = [elem1]; // Expecting an array

        service.getQueuedBuildJobsByCourseId(courseId).subscribe((data) => {
            expect(data).toEqual(expectedResponse); // Check if the response matches expected
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/queued/${courseId}`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse); // Flush an array of elements
    });

    it('should return running build jobs for a specific course', () => {
        const courseId = 1;
        const expectedResponse = [elem1]; // Assuming this is your expected response

        service.getRunningBuildJobsByCourseId(courseId).subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/running/${courseId}`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should return all queued build jobs', () => {
        const expectedResponse = [elem1]; // Assuming this is your expected response

        service.getQueuedBuildJobs().subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/queued`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should return all running build jobs', () => {
        const expectedResponse = [elem1]; // Assuming this is your expected response

        service.getRunningBuildJobs().subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/running`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should cancel a specific build job in a course', () => {
        const courseId = 1;
        const buildJobId = 1;

        service.cancelBuildJobInCourse(courseId, buildJobId).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/cancel/${courseId}/${buildJobId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel a specific build job', () => {
        const buildJobId = 1;

        service.cancelBuildJob(buildJobId).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel/${buildJobId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all running build jobs', () => {
        service.cancelAllRunningBuildJobs().subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-all-running`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all running build jobs in a course', () => {
        const courseId = 1;

        service.cancelAllRunningBuildJobsInCourse(courseId).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/cancel-all-running/${courseId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all queued build jobs', () => {
        service.cancelAllQueuedBuildJobs().subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-all-queued`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all queued build jobs in a course', () => {
        const courseId = 1;

        service.cancelAllQueuedBuildJobsInCourse(courseId).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/cancel-all-queued/${courseId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    afterEach(() => {
        httpMock.verify(); // Verify that there are no outstanding requests.
    });
});

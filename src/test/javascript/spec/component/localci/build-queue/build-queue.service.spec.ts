import { TestBed, fakeAsync, tick } from '@angular/core/testing';

import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { BuildJob } from 'app/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { RepositoryInfo, TriggeredByPushTo } from 'app/entities/repository-info.model';
import { JobTimingInfo } from 'app/entities/job-timing-info.model';
import { BuildConfig } from 'app/entities/build-config.model';

describe('BuildQueueService', () => {
    let service: BuildQueueService;
    let httpMock: HttpTestingController;
    let elem1: BuildJob;
    let repositoryInfo: RepositoryInfo;
    let jobTimingInfo: JobTimingInfo;
    let buildConfig: BuildConfig;

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
        repositoryInfo = new RepositoryInfo();
        jobTimingInfo = new JobTimingInfo();
        buildConfig = new BuildConfig();

        repositoryInfo.repositoryName = 'name1';
        repositoryInfo.repositoryType = 'USER';
        repositoryInfo.triggeredByPushTo = TriggeredByPushTo.USER;
        repositoryInfo.assignmentRepositoryUri = 'uri1';
        repositoryInfo.testRepositoryUri = 'uri2';
        repositoryInfo.solutionRepositoryUri = 'uri3';
        repositoryInfo.auxiliaryRepositoryUris = ['uri4'];
        repositoryInfo.auxiliaryRepositoryCheckoutDirectories = ['dir1'];

        jobTimingInfo.submissionDate = dayjs('2023-01-02');
        jobTimingInfo.buildStartDate = dayjs('2023-01-02');
        jobTimingInfo.buildCompletionDate = dayjs('2023-01-02');

        buildConfig.dockerImage = 'image1';
        buildConfig.commitHash = 'hash1';
        buildConfig.branch = 'branch1';
        buildConfig.programmingLanguage = 'lang1';
        buildConfig.projectType = 'type1';
        buildConfig.scaEnabled = false;
        buildConfig.sequentialTestRunsEnabled = false;
        buildConfig.testwiseCoverageEnabled = false;
        buildConfig.resultPaths = ['path1'];

        elem1.id = '1';
        elem1.name = 'test1';
        elem1.participationId = 1;
        elem1.retryCount = 1;
        elem1.priority = 1;
        elem1.courseId = 1;
        elem1.repositoryInfo = repositoryInfo;
        elem1.jobTimingInfo = jobTimingInfo;
        elem1.buildConfig = buildConfig;
    });

    it('should return build job for course', () => {
        const courseId = 1;
        const expectedResponse = [elem1]; // Expecting an array

        service.getQueuedBuildJobsByCourseId(courseId).subscribe((data) => {
            expect(data).toEqual(expectedResponse); // Check if the response matches expected
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/courses/${courseId}/queued-jobs`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse); // Flush an array of elements
    });

    it('should return running build jobs for a specific course', () => {
        const courseId = 1;
        const expectedResponse = [elem1]; // Assuming this is your expected response

        service.getRunningBuildJobsByCourseId(courseId).subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/courses/${courseId}/running-jobs`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should return all queued build jobs', () => {
        const expectedResponse = [elem1]; // Assuming this is your expected response

        service.getQueuedBuildJobs().subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/queued-jobs`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should return all running build jobs', () => {
        const expectedResponse = [elem1]; // Assuming this is your expected response

        service.getRunningBuildJobs().subscribe((data) => {
            expect(data).toEqual(expectedResponse);
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/running-jobs`);
        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);
    });

    it('should cancel a specific build job in a course', () => {
        const courseId = 1;
        const buildJobId = '1';

        service.cancelBuildJobInCourse(courseId, buildJobId).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/courses/${courseId}/cancel-job/${buildJobId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel a specific build job', () => {
        const buildJobId = '1';

        service.cancelBuildJob(buildJobId).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-job/${buildJobId}`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all running build jobs', () => {
        service.cancelAllRunningBuildJobs().subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-all-running-jobs`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all running build jobs in a course', () => {
        const courseId = 1;

        service.cancelAllRunningBuildJobsInCourse(courseId).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/courses/${courseId}/cancel-all-running-jobs`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all queued build jobs', () => {
        service.cancelAllQueuedBuildJobs().subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-all-queued-jobs`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all queued build jobs in a course', () => {
        const courseId = 1;

        service.cancelAllQueuedBuildJobsInCourse(courseId).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/courses/${courseId}/cancel-all-queued-jobs`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should cancel all running build jobs for a specific agent', () => {
        const agentName = 'agent1';

        service.cancelAllRunningBuildJobsForAgent(agentName).subscribe(() => {
            // Ensure that the cancellation was successful
            expect(true).toBeTrue();
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-all-running-jobs-for-agent?agentName=${agentName}`);
        expect(req.request.method).toBe('DELETE');
        req.flush({}); // Flush an empty response to indicate success
    });

    it('should handle errors when cancelling a specific build job', fakeAsync(() => {
        const buildJobId = '1';

        let errorOccurred = false;

        service.cancelBuildJob(buildJobId).subscribe({
            error: (err) => {
                // Ensure that the error is handled properly
                expect(err.message).toBe(
                    'Failed to cancel build job ' +
                        buildJobId +
                        '\nHttp failure response for ' +
                        service.adminResourceUrl +
                        '/cancel-job/' +
                        buildJobId +
                        ': 500 Internal Server Error',
                );
                errorOccurred = true;
            },
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-job/${buildJobId}`);
        expect(req.request.method).toBe('DELETE');

        // Simulate an error response from the server
        req.flush(null, { status: 500, statusText: 'Internal Server Error' });

        tick();

        // Verify that an error occurred during the subscription
        expect(errorOccurred).toBeTrue();
    }));

    it('should handle errors when cancelling a specific build job in a course', fakeAsync(() => {
        const courseId = 1;
        const buildJobId = '1';

        let errorOccurred = false;

        service.cancelBuildJobInCourse(courseId, buildJobId).subscribe({
            error: (err) => {
                // Ensure that the error is handled properly
                expect(err.message).toBe(
                    'Failed to cancel build job ' +
                        buildJobId +
                        ' in course ' +
                        courseId +
                        '\nHttp failure response for ' +
                        service.resourceUrl +
                        '/courses/' +
                        courseId +
                        '/cancel-job/' +
                        buildJobId +
                        ': 500 Internal Server Error',
                );
                errorOccurred = true;
            },
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/courses/${courseId}/cancel-job/${buildJobId}`);
        expect(req.request.method).toBe('DELETE');

        // Simulate an error response from the server
        req.flush(null, { status: 500, statusText: 'Internal Server Error' });

        tick();

        // Verify that an error occurred during the subscription
        expect(errorOccurred).toBeTrue();
    }));

    it('should handle errors when cancelling all running build jobs', fakeAsync(() => {
        let errorOccurred = false;

        service.cancelAllRunningBuildJobs().subscribe({
            error: (err) => {
                // Ensure that the error is handled properly
                expect(err.message).toBe(
                    'Failed to cancel all running build jobs' + '\nHttp failure response for ' + service.adminResourceUrl + '/cancel-all-running-jobs: 500 Internal Server Error',
                );
                errorOccurred = true;
            },
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-all-running-jobs`);
        expect(req.request.method).toBe('DELETE');

        // Simulate an error response from the server
        req.flush(null, { status: 500, statusText: 'Internal Server Error' });

        tick();

        // Verify that an error occurred during the subscription
        expect(errorOccurred).toBeTrue();
    }));

    it('should handle errors when cancelling all running build jobs in a course', fakeAsync(() => {
        const courseId = 1;

        let errorOccurred = false;

        service.cancelAllRunningBuildJobsInCourse(courseId).subscribe({
            error: (err) => {
                // Ensure that the error is handled properly
                expect(err.message).toBe(
                    'Failed to cancel all running build jobs in course ' +
                        courseId +
                        '\nHttp failure response for ' +
                        service.resourceUrl +
                        '/courses/' +
                        courseId +
                        '/cancel-all-running-jobs: 500 Internal Server Error',
                );
                errorOccurred = true;
            },
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/courses/${courseId}/cancel-all-running-jobs`);
        expect(req.request.method).toBe('DELETE');

        // Simulate an error response from the server
        req.flush(null, { status: 500, statusText: 'Internal Server Error' });

        tick();

        // Verify that an error occurred during the subscription
        expect(errorOccurred).toBeTrue();
    }));

    it('should handle errors when cancelling all queued build jobs', fakeAsync(() => {
        let errorOccurred = false;

        service.cancelAllQueuedBuildJobs().subscribe({
            error: (err) => {
                // Ensure that the error is handled properly
                expect(err.message).toBe(
                    'Failed to cancel all queued build jobs' + '\nHttp failure response for ' + service.adminResourceUrl + '/cancel-all-queued-jobs: 500 Internal Server Error',
                );
                errorOccurred = true;
            },
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-all-queued-jobs`);
        expect(req.request.method).toBe('DELETE');

        // Simulate an error response from the server
        req.flush(null, { status: 500, statusText: 'Internal Server Error' });

        tick();

        // Verify that an error occurred during the subscription
        expect(errorOccurred).toBeTrue();
    }));

    it('should handle errors when cancelling all queued build jobs in a course', fakeAsync(() => {
        const courseId = 1;

        let errorOccurred = false;

        service.cancelAllQueuedBuildJobsInCourse(courseId).subscribe({
            error: (err) => {
                // Ensure that the error is handled properly
                expect(err.message).toBe(
                    'Failed to cancel all queued build jobs in course ' +
                        courseId +
                        '\nHttp failure response for ' +
                        service.resourceUrl +
                        '/courses/' +
                        courseId +
                        '/cancel-all-queued-jobs: 500 Internal Server Error',
                );
                errorOccurred = true;
            },
        });

        const req = httpMock.expectOne(`${service.resourceUrl}/courses/${courseId}/cancel-all-queued-jobs`);
        expect(req.request.method).toBe('DELETE');

        // Simulate an error response from the server
        req.flush(null, { status: 500, statusText: 'Internal Server Error' });

        tick();

        // Verify that an error occurred during the subscription
        expect(errorOccurred).toBeTrue();
    }));

    it('should handle errors when cancelling all running build jobs for a specific agent', fakeAsync(() => {
        const agentName = 'agent1';

        let errorOccurred = false;

        service.cancelAllRunningBuildJobsForAgent(agentName).subscribe({
            error: (err) => {
                // Ensure that the error is handled properly
                expect(err.message).toBe(
                    'Failed to cancel all running build jobs for agent ' +
                        agentName +
                        '\nHttp failure response for ' +
                        service.adminResourceUrl +
                        '/cancel-all-running-jobs-for-agent?agentName=' +
                        agentName +
                        ': 500 Internal Server Error',
                );
                errorOccurred = true;
            },
        });

        const req = httpMock.expectOne(`${service.adminResourceUrl}/cancel-all-running-jobs-for-agent?agentName=${agentName}`);
        expect(req.request.method).toBe('DELETE');

        // Simulate an error response from the server
        req.flush(null, { status: 500, statusText: 'Internal Server Error' });

        tick();

        // Verify that an error occurred during the subscription
        expect(errorOccurred).toBeTrue();
    }));

    afterEach(() => {
        httpMock.verify(); // Verify that there are no outstanding requests.
    });
});

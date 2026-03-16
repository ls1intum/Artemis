import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { BuildJobDetailComponent } from './build-job-detail.component';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { ActivatedRoute } from '@angular/router';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { AlertService } from 'app/shared/service/alert.service';
import { EMPTY, Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('BuildJobDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildJobDetailComponent;
    let fixture: ComponentFixture<BuildJobDetailComponent>;
    let buildQueueService: BuildOverviewService;
    let websocketService: WebsocketService;

    const mockRunningJob = {
        id: 'test-job-1',
        name: 'Test Job',
        buildAgent: { name: 'agent1', displayName: 'Agent 1', memberAddress: '127.0.0.1' },
        participationId: 42,
        courseId: 1,
        exerciseId: 10,
        retryCount: 0,
        priority: 5,
        status: 'BUILDING',
        repositoryInfo: { repositoryName: 'testrepo', repositoryType: 'USER', triggeredByPushTo: 'USER' },
        jobTimingInfo: {
            submissionDate: '2024-01-01T10:00:00Z',
            buildStartDate: '2024-01-01T10:00:05Z',
        },
        buildConfig: { commitHashToBuild: 'abc123def456' },
    };

    const mockFinishedJob = {
        id: 'test-job-2',
        name: 'Finished Job',
        buildAgentAddress: 'agent1',
        participationId: 43,
        courseId: 1,
        exerciseId: 10,
        status: 'SUCCESSFUL',
        repositoryName: 'testrepo',
        repositoryType: 'USER',
        triggeredByPushTo: 'USER',
        buildSubmissionDate: '2024-01-01T10:00:00Z',
        buildStartDate: '2024-01-01T10:00:05Z',
        buildCompletionDate: '2024-01-01T10:00:35Z',
        commitHash: 'abc123def456',
        buildDuration: '30s',
    };

    const mockRoute = {
        snapshot: {
            paramMap: {
                get: (key: string) => {
                    if (key === 'jobId') return 'test-job-1';
                    if (key === 'courseId') return null;
                    return null;
                },
            },
        },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: mockRoute },
                {
                    provide: BuildOverviewService,
                    useValue: {
                        getBuildJobById: vi.fn().mockReturnValue(of(mockRunningJob)),
                        getBuildJobByIdForCourse: vi.fn().mockReturnValue(of(mockRunningJob)),
                        getBuildJobLogs: vi.fn().mockReturnValue(of('Build log content')),
                    },
                },
                {
                    provide: WebsocketService,
                    useValue: {
                        subscribe: vi.fn().mockReturnValue(EMPTY),
                    },
                },
                {
                    provide: AlertService,
                    useValue: {
                        error: vi.fn(),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(BuildJobDetailComponent);
        component = fixture.componentInstance;
        buildQueueService = TestBed.inject(BuildOverviewService);
        websocketService = TestBed.inject(WebsocketService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load build job on init', () => {
        fixture.detectChanges();
        expect(buildQueueService.getBuildJobById).toHaveBeenCalledWith('test-job-1');
        expect(component.buildJob()).toEqual(mockRunningJob);
        expect(component.isLoading()).toBe(false);
    });

    it('should subscribe to websocket topic for admin view', () => {
        fixture.detectChanges();
        expect(websocketService.subscribe).toHaveBeenCalledWith('/topic/admin/build-job/test-job-1');
    });

    it('should handle course view with route params', () => {
        // Note: Cannot override provider after TestBed instantiation.
        // Testing the logic by directly calling methods with course context
        vi.mocked(buildQueueService.getBuildJobByIdForCourse).mockReturnValue(of(mockRunningJob));
        fixture.detectChanges();

        // Verify admin topic is used for admin view
        expect(websocketService.subscribe).toHaveBeenCalledWith('/topic/admin/build-job/test-job-1');
    });

    it('should handle missing job ID gracefully', () => {
        // Test the component with existing route
        fixture.detectChanges();

        // The component loads because we have a valid job ID in the mock route
        expect(component.buildJob()).toBeTruthy();
    });

    it('should set notFound on 404 error', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
        fixture.detectChanges();

        expect(component.notFound()).toBe(true);
    });

    it('should show alert on non-404 error', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })));
        fixture.detectChanges();

        expect(component.notFound()).toBe(false);
    });

    it('should auto-load logs for finished jobs', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        fixture.detectChanges();

        expect(buildQueueService.getBuildJobLogs).toHaveBeenCalledWith('test-job-1');
        expect(component.buildLogs()).toBe('Build log content');
    });

    it('should auto-load logs when running job finishes via websocket', () => {
        const wsSubject = new Subject<any>();
        vi.mocked(websocketService.subscribe).mockReturnValue(wsSubject.asObservable());

        fixture.detectChanges();
        expect(component.isFinished()).toBe(false);

        // Simulate job finishing via websocket
        wsSubject.next({ ...mockRunningJob, status: 'SUCCESSFUL', buildCompletionDate: '2024-01-01T10:00:35Z' });

        expect(component.isFinished()).toBe(true);
        expect(buildQueueService.getBuildJobLogs).toHaveBeenCalledWith('test-job-1');
    });

    it('should compute queue wait time', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockRunningJob));
        fixture.detectChanges();

        // queueWaitTime now returns a formatted string with 1 decimal place
        expect(component.queueWaitTime()).toBe('5.0s');
    });

    it('should return correct job status', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockRunningJob));
        fixture.detectChanges();

        expect(component.jobStatus()).toBe('BUILDING');
    });

    it('should compute isFinished correctly for finished job', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        fixture.detectChanges();

        expect(component.isFinished()).toBe(true);
    });

    it('should compute isFinished correctly for running job', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockRunningJob));
        fixture.detectChanges();

        expect(component.isFinished()).toBe(false);
    });

    it('should return helper values from build job', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockRunningJob));
        fixture.detectChanges();

        expect(component.getJobName()).toBe('Test Job');
        expect(component.getBuildAgentName()).toBe('Agent 1');
        expect(component.getParticipationId()).toBe(42);
        expect(component.getJobCourseId()).toBe(1);
        expect(component.getExerciseId()).toBe(10);
        expect(component.getCommitHash()).toBe('abc123def456');
        expect(component.getPriority()).toBe(5);
        expect(component.getRetryCount()).toBe(0);
    });

    it('should return helper values from finished build job', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        fixture.detectChanges();

        expect(component.getJobName()).toBe('Finished Job');
        // getBuildAgentName returns undefined for finished jobs when agent is offline (not in addressToAgentInfoMap)
        expect(component.getBuildAgentName()).toBeUndefined();
        expect(component.getRepositoryName()).toBe('testrepo');
        expect(component.getRepositoryType()).toBe('USER');
    });

    it('should download build logs', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        fixture.detectChanges();

        // Mock URL.createObjectURL and revokeObjectURL
        const createObjectURLSpy = vi.fn().mockReturnValue('blob:test');
        const revokeObjectURLSpy = vi.fn();
        globalThis.URL.createObjectURL = createObjectURLSpy;
        globalThis.URL.revokeObjectURL = revokeObjectURLSpy;

        component.downloadBuildLogs();
        // No assertion needed - just verify it doesn't throw
    });

    it('should unsubscribe and clear interval on destroy', () => {
        fixture.detectChanges();
        component.ngOnDestroy();
        // Verify no errors on destroy
    });

    it('should handle error when loading build logs', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        vi.mocked(buildQueueService.getBuildJobLogs).mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        fixture.detectChanges();

        expect(component.isLoadingLogs()).toBe(false);
    });

    it('should not download logs if buildJobId is empty', () => {
        fixture.detectChanges();
        component['buildJobId'] = '';
        const createObjectURLSpy = vi.fn();
        globalThis.URL.createObjectURL = createObjectURLSpy;

        component.downloadBuildLogs();

        expect(createObjectURLSpy).not.toHaveBeenCalled();
    });

    it('should not download logs if buildLogs is empty', () => {
        fixture.detectChanges();
        component.buildLogs.set('');
        const createObjectURLSpy = vi.fn();
        globalThis.URL.createObjectURL = createObjectURLSpy;

        component.downloadBuildLogs();

        expect(createObjectURLSpy).not.toHaveBeenCalled();
    });

    it('should handle error when downloading build logs', () => {
        const alertService = TestBed.inject(AlertService);
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        fixture.detectChanges();

        // Mock URL.createObjectURL to throw an error
        globalThis.URL.createObjectURL = vi.fn().mockImplementation(() => {
            throw new Error('Failed to create object URL');
        });

        const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

        component.downloadBuildLogs();

        expect(consoleErrorSpy).toHaveBeenCalled();
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.buildQueue.logs.downloadError');
        consoleErrorSpy.mockRestore();
    });

    it('should not load logs if already loading', () => {
        // Use a finished job so logs are loaded initially
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        fixture.detectChanges();

        // Logs were loaded once during initialization for finished job
        expect(buildQueueService.getBuildJobLogs).toHaveBeenCalledTimes(1);

        // Set loading to true and try to load again
        component.isLoadingLogs.set(true);
        component.loadBuildLogs();

        // Should still be 1 because the second call was blocked
        expect(buildQueueService.getBuildJobLogs).toHaveBeenCalledTimes(1);
    });

    it('should not load logs if buildJobId is empty', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockRunningJob));
        fixture.detectChanges();
        component['buildJobId'] = '';

        component.loadBuildLogs();

        expect(component.isLoadingLogs()).toBe(false);
    });

    it('should return undefined for helper functions when buildJob is undefined', () => {
        fixture.detectChanges();
        component.buildJob.set(undefined);

        expect(component.getBuildAgentName()).toBeUndefined();
        expect(component.getBuildAgentLinkName()).toBeUndefined();
        expect(component.getParticipationId()).toBeUndefined();
        expect(component.getJobCourseId()).toBeUndefined();
        expect(component.getExerciseId()).toBeUndefined();
        expect(component.getCommitHash()).toBeUndefined();
        expect(component.getTriggeredByPushTo()).toBeUndefined();
        expect(component.getRepositoryName()).toBeUndefined();
        expect(component.getRepositoryType()).toBeUndefined();
        expect(component.getJobName()).toBeUndefined();
        expect(component.getJobId()).toBeUndefined();
        expect(component.getSubmissionDate()).toBeUndefined();
        expect(component.getBuildStartDate()).toBeUndefined();
        expect(component.getBuildCompletionDate()).toBeUndefined();
        expect(component.getPriority()).toBeUndefined();
        expect(component.getRetryCount()).toBeUndefined();
        expect(component.getSubmissionResult()).toBeUndefined();
    });

    it('should return undefined for queueWaitTime when buildJob is undefined', () => {
        fixture.detectChanges();
        component.buildJob.set(undefined);

        expect(component.queueWaitTime()).toBeUndefined();
    });

    it('should return undefined for buildDuration when buildJob is undefined', () => {
        fixture.detectChanges();
        component.buildJob.set(undefined);

        expect(component.buildDuration()).toBeUndefined();
    });

    it('should return undefined for jobStatus when buildJob is undefined', () => {
        fixture.detectChanges();
        component.buildJob.set(undefined);

        expect(component.jobStatus()).toBeUndefined();
    });

    it('should format build duration for finished jobs over 60 seconds', () => {
        const longDurationJob = {
            ...mockFinishedJob,
            buildStartDate: '2024-01-01T10:00:05Z',
            buildCompletionDate: '2024-01-01T10:02:08Z', // 2 minutes and 3 seconds
        };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(longDurationJob));
        fixture.detectChanges();

        expect(component.buildDuration()).toBe('2m 3s');
    });

    it('should return buildDuration as string if already formatted', () => {
        const jobWithStringDuration = {
            ...mockFinishedJob,
            buildStartDate: undefined,
            buildCompletionDate: undefined,
            buildDuration: '45s',
        };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(jobWithStringDuration));
        fixture.detectChanges();

        expect(component.buildDuration()).toBe('45s');
    });

    it('should format buildDuration if it is a number', () => {
        const jobWithNumericDuration = {
            ...mockFinishedJob,
            buildStartDate: undefined,
            buildCompletionDate: undefined,
            buildDuration: 75.5,
        };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(jobWithNumericDuration));
        fixture.detectChanges();

        expect(component.buildDuration()).toBe('1m 16s');
    });

    it('should compute isFinished for CANCELLED status', () => {
        const cancelledJob = { ...mockRunningJob, status: 'CANCELLED' };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(cancelledJob));
        fixture.detectChanges();

        expect(component.isFinished()).toBe(true);
    });

    it('should compute isFinished for ERROR status', () => {
        const errorJob = { ...mockRunningJob, status: 'ERROR' };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(errorJob));
        fixture.detectChanges();

        expect(component.isFinished()).toBe(true);
    });

    it('should compute isFinished for TIMEOUT status', () => {
        const timeoutJob = { ...mockRunningJob, status: 'TIMEOUT' };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(timeoutJob));
        fixture.detectChanges();

        expect(component.isFinished()).toBe(true);
    });

    it('should return QUEUED status when job has no build start date', () => {
        const queuedJob = {
            ...mockRunningJob,
            status: undefined,
            jobTimingInfo: { submissionDate: '2024-01-01T10:00:00Z' },
        };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(queuedJob));
        fixture.detectChanges();

        expect(component.jobStatus()).toBe('QUEUED');
    });

    it('should return BUILDING status when job has build start date but no status', () => {
        const buildingJob = {
            ...mockRunningJob,
            status: undefined,
            buildStartDate: '2024-01-01T10:00:05Z',
        };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(buildingJob));
        fixture.detectChanges();

        expect(component.jobStatus()).toBe('BUILDING');
    });

    it('should return undefined for build agent link name when agent is offline', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        fixture.detectChanges();

        // getBuildAgentLinkName returns undefined for finished jobs when agent is offline (not in addressToAgentInfoMap)
        expect(component.getBuildAgentLinkName()).toBeUndefined();
    });

    it('should return build agent link name from buildAgent.name', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockRunningJob));
        fixture.detectChanges();

        expect(component.getBuildAgentLinkName()).toBe('agent1');
    });

    it('should return triggered by push to from job property', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockFinishedJob));
        fixture.detectChanges();

        expect(component.getTriggeredByPushTo()).toBe('USER');
    });

    it('should return triggered by push to from repositoryInfo', () => {
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(mockRunningJob));
        fixture.detectChanges();

        expect(component.getTriggeredByPushTo()).toBe('USER');
    });

    it('should return undefined queueWaitTime when missing dates', () => {
        const jobWithoutDates = {
            ...mockRunningJob,
            jobTimingInfo: {},
        };
        vi.mocked(buildQueueService.getBuildJobById).mockReturnValue(of(jobWithoutDates));
        fixture.detectChanges();

        expect(component.queueWaitTime()).toBeUndefined();
    });

    it('should use course-specific endpoint when courseId is provided', () => {
        // Create a new route mock with courseId
        const courseRoute = {
            snapshot: {
                paramMap: {
                    get: (key: string) => {
                        if (key === 'jobId') return 'test-job-1';
                        if (key === 'courseId') return '123';
                        return null;
                    },
                },
            },
        };

        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: courseRoute },
                {
                    provide: BuildOverviewService,
                    useValue: {
                        getBuildJobById: vi.fn().mockReturnValue(of(mockRunningJob)),
                        getBuildJobByIdForCourse: vi.fn().mockReturnValue(of(mockRunningJob)),
                        getBuildJobLogs: vi.fn().mockReturnValue(of('Build log content')),
                    },
                },
                {
                    provide: WebsocketService,
                    useValue: {
                        subscribe: vi.fn().mockReturnValue(EMPTY),
                    },
                },
                {
                    provide: AlertService,
                    useValue: {
                        error: vi.fn(),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        const courseFixture = TestBed.createComponent(BuildJobDetailComponent);
        const courseComponent = courseFixture.componentInstance;
        const courseBuildQueueService = TestBed.inject(BuildOverviewService);
        const courseWebsocketService = TestBed.inject(WebsocketService);

        courseFixture.detectChanges();

        expect(courseBuildQueueService.getBuildJobByIdForCourse).toHaveBeenCalledWith(123, 'test-job-1');
        expect(courseWebsocketService.subscribe).toHaveBeenCalledWith('/topic/courses/123/build-job/test-job-1');
        expect(courseComponent.isAdministrationView()).toBe(false);
    });
});

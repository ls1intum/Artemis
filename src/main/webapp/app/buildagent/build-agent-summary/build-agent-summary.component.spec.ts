import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { BuildAgentSummaryComponent } from 'app/buildagent/build-agent-summary/build-agent-summary.component';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Subject, of, throwError } from 'rxjs';
import { BuildJob } from 'app/buildagent/shared/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { MockProvider } from 'ng-mocks';
import { BuildAgentInformation, BuildAgentStatus } from 'app/buildagent/shared/entities/build-agent-information.model';
import { RepositoryInfo, TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { JobTimingInfo } from 'app/buildagent/shared/entities/job-timing-info.model';
import { BuildConfig } from 'app/buildagent/shared/entities/build-config.model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { BuildAgentsService } from 'app/buildagent/build-agents.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Router } from '@angular/router';

describe('BuildAgentSummaryComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildAgentSummaryComponent;
    let fixture: ComponentFixture<BuildAgentSummaryComponent>;

    const mockWebsocketService = {
        subscribe: vi.fn(),
    };

    const mockBuildAgentsService = {
        getBuildAgentSummary: vi.fn().mockReturnValue(of([])),
        pauseAllBuildAgents: vi.fn().mockReturnValue(of({})),
        resumeAllBuildAgents: vi.fn().mockReturnValue(of({})),
        clearDistributedData: vi.fn().mockReturnValue(of({})),
    };

    const repositoryInfo: RepositoryInfo = {
        repositoryName: 'repo2',
        repositoryType: 'USER',
        triggeredByPushTo: TriggeredByPushTo.USER,
        assignmentRepositoryUri: 'https://some.uri',
        testRepositoryUri: 'https://some.uri',
        solutionRepositoryUri: 'https://some.uri',
        auxiliaryRepositoryUris: [],
        auxiliaryRepositoryCheckoutDirectories: [],
    };

    const jobTimingInfo1: JobTimingInfo = {
        submissionDate: dayjs('2023-01-01'),
        buildStartDate: dayjs('2023-01-01'),
        buildCompletionDate: dayjs('2023-01-02'),
        buildDuration: undefined,
    };

    const buildConfig: BuildConfig = {
        dockerImage: 'someImage',
        commitHashToBuild: 'abc124',
        branch: 'main',
        programmingLanguage: 'Java',
        projectType: 'Maven',
        scaEnabled: false,
        sequentialTestRunsEnabled: false,
        resultPaths: [],
    };

    const mockRunningJobs1: BuildJob[] = [
        {
            id: '2',
            name: 'Build Job 2',
            buildAgent: { name: 'agent2', memberAddress: 'localhost:8080', displayName: 'Agent 2' },
            participationId: 102,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 3,
            repositoryInfo: repositoryInfo,
            jobTimingInfo: jobTimingInfo1,
            buildConfig: buildConfig,
        },
        {
            id: '4',
            name: 'Build Job 4',
            buildAgent: { name: 'agent4', memberAddress: 'localhost:8080', displayName: 'Agent 4' },
            participationId: 104,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 2,
            repositoryInfo: repositoryInfo,
            jobTimingInfo: jobTimingInfo1,
            buildConfig: buildConfig,
        },
    ];

    const mockBuildAgents: BuildAgentInformation[] = [
        {
            id: 1,
            buildAgent: { name: 'buildagent1', displayName: 'Build Agent 1', memberAddress: 'agent1' },
            maxNumberOfConcurrentBuildJobs: 2,
            numberOfCurrentBuildJobs: 2,
            status: BuildAgentStatus.ACTIVE,
        },
        {
            id: 2,
            buildAgent: { name: 'buildagent2', displayName: 'Build Agent 2', memberAddress: 'agent2' },
            maxNumberOfConcurrentBuildJobs: 2,
            numberOfCurrentBuildJobs: 2,
            status: BuildAgentStatus.ACTIVE,
        },
    ];
    let websocketSubject: Subject<BuildAgentInformation[]>;
    let alertService: AlertService;
    let alertServiceAddAlertStub: ReturnType<typeof vi.spyOn>;
    let modalService: NgbModal;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            declarations: [],
            providers: [
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: BuildAgentsService, useValue: mockBuildAgentsService },
                { provide: DataTableComponent, useClass: DataTableComponent },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(BuildAgentSummaryComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        modalService = TestBed.inject(NgbModal);
        alertServiceAddAlertStub = vi.spyOn(alertService, 'addAlert');

        websocketSubject = new Subject<BuildAgentInformation[]>();
        mockWebsocketService.subscribe.mockReturnValue(websocketSubject.asObservable());
        vi.clearAllMocks();
    });

    it('should load build agents on initialization', () => {
        mockBuildAgentsService.getBuildAgentSummary.mockReturnValue(of(mockBuildAgents));

        component.ngOnInit();

        expect(mockBuildAgentsService.getBuildAgentSummary).toHaveBeenCalled();
        expect(component.buildAgents()).toEqual(mockBuildAgents);
        expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/topic/admin/build-agents');
    });

    it('should unsubscribe from the websocket channel on destruction', () => {
        component.ngOnInit();
        const unsubscribeSpy = vi.spyOn(component.buildAgentsWebsocketSubscription!, 'unsubscribe');

        component.ngOnDestroy();

        expect(unsubscribeSpy).toHaveBeenCalled();
    });

    it('should cancel a build job', () => {
        const buildJob = mockRunningJobs1[0];
        const spy = vi.spyOn(component, 'cancelBuildJob');

        component.ngOnInit();
        component.cancelBuildJob(buildJob.id!);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(buildJob.id!);
    });

    it('should cancel all build jobs of a build agent', () => {
        const buildAgent = mockBuildAgents[0];
        const spy = vi.spyOn(component, 'cancelAllBuildJobs');

        component.ngOnInit();
        component.cancelAllBuildJobs(buildAgent.buildAgent);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(buildAgent.buildAgent);
    });

    it('should calculate the build capacity and current builds', () => {
        component.ngOnInit();
        websocketSubject.next(mockBuildAgents);

        expect(component.buildCapacity()).toBe(4);
        expect(component.currentBuilds()).toBe(4);
    });

    it('should calculate the build capacity and current builds when there are no build agents', () => {
        component.ngOnInit();
        websocketSubject.next([]);

        expect(component.buildCapacity()).toBe(0);
        expect(component.currentBuilds()).toBe(0);
    });

    it('should call correct service method when pausing and resuming build agents', () => {
        component.pauseAllBuildAgents();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.SUCCESS,
            message: 'artemisApp.buildAgents.alerts.buildAgentsPaused',
        });

        component.resumeAllBuildAgents();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.SUCCESS,
            message: 'artemisApp.buildAgents.alerts.buildAgentsResumed',
        });
    });

    it('should show alert when error in pausing or resuming build agents', () => {
        mockBuildAgentsService.pauseAllBuildAgents.mockReturnValue(throwError(() => new Error()));

        component.pauseAllBuildAgents();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'artemisApp.buildAgents.alerts.buildAgentPauseFailed',
        });

        mockBuildAgentsService.resumeAllBuildAgents.mockReturnValue(throwError(() => new Error()));

        component.resumeAllBuildAgents();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'artemisApp.buildAgents.alerts.buildAgentResumeFailed',
        });
    });

    it('should call correct service method when clearing distributed data', () => {
        component.clearDistributedData();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.SUCCESS,
            message: 'artemisApp.buildAgents.alerts.distributedDataCleared',
        });
    });

    it('should show alert when error in clearing distributed data', () => {
        mockBuildAgentsService.clearDistributedData.mockReturnValue(throwError(() => new Error()));

        component.clearDistributedData();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'artemisApp.buildAgents.alerts.distributedDataClearFailed',
        });
    });

    it('should correctly open modals', () => {
        const modalRef = {
            result: Promise.resolve('close'),
        } as NgbModalRef;
        const openSpy = vi.spyOn(modalService, 'open').mockReturnValue(modalRef);

        component.displayPauseBuildAgentModal();
        expect(openSpy).toHaveBeenCalledOnce();

        component.displayClearDistributedDataModal();
        expect(openSpy).toHaveBeenCalledTimes(2);
    });

    it('should not cancel all build jobs when buildAgent is undefined', () => {
        const spy = vi.spyOn(mockBuildAgentsService, 'getBuildAgentSummary');

        component.ngOnInit();
        component.cancelAllBuildJobs(undefined);

        // Should not call any service to cancel jobs
        expect(spy).toHaveBeenCalledOnce(); // Only the initial load call
    });

    it('should not cancel all build jobs when buildAgent.name is undefined', () => {
        const spy = vi.spyOn(mockBuildAgentsService, 'getBuildAgentSummary');

        component.ngOnInit();
        component.cancelAllBuildJobs({ name: '', memberAddress: 'test', displayName: 'Test' });

        // Should not call any service to cancel jobs
        expect(spy).toHaveBeenCalledOnce(); // Only the initial load call
    });

    it('should not cancel all build jobs when no matching agent found', () => {
        mockBuildAgentsService.getBuildAgentSummary.mockReturnValue(of(mockBuildAgents));

        component.ngOnInit();
        component.cancelAllBuildJobs({ name: 'nonexistent-agent', memberAddress: 'test', displayName: 'Test' });

        // The method should not throw, just not find a matching agent
        expect(component.buildAgents()).toEqual(mockBuildAgents);
    });

    it('should call pauseAllBuildAgents when modal is confirmed', async () => {
        mockBuildAgentsService.getBuildAgentSummary.mockReturnValue(of(mockBuildAgents));
        mockBuildAgentsService.pauseAllBuildAgents.mockReturnValue(of({}));

        const modalRef = {
            result: Promise.resolve(true),
        } as NgbModalRef;
        vi.spyOn(modalService, 'open').mockReturnValue(modalRef);

        component.ngOnInit();
        component.displayPauseBuildAgentModal();

        await modalRef.result;

        expect(mockBuildAgentsService.pauseAllBuildAgents).toHaveBeenCalled();
    });

    it('should call clearDistributedData when modal is confirmed', async () => {
        mockBuildAgentsService.getBuildAgentSummary.mockReturnValue(of(mockBuildAgents));
        mockBuildAgentsService.clearDistributedData.mockReturnValue(of({}));

        const modalRef = {
            result: Promise.resolve(true),
        } as NgbModalRef;
        vi.spyOn(modalService, 'open').mockReturnValue(modalRef);

        component.ngOnInit();
        component.displayClearDistributedDataModal();

        await modalRef.result;

        expect(mockBuildAgentsService.clearDistributedData).toHaveBeenCalled();
    });

    it('should not call pauseAllBuildAgents when modal is cancelled', async () => {
        mockBuildAgentsService.pauseAllBuildAgents.mockClear();

        const modalRef = {
            result: Promise.resolve(false),
        } as NgbModalRef;
        vi.spyOn(modalService, 'open').mockReturnValue(modalRef);

        component.displayPauseBuildAgentModal();

        await modalRef.result;

        expect(mockBuildAgentsService.pauseAllBuildAgents).not.toHaveBeenCalled();
    });

    it('should not call clearDistributedData when modal is cancelled', async () => {
        mockBuildAgentsService.clearDistributedData.mockClear();

        const modalRef = {
            result: Promise.resolve(false),
        } as NgbModalRef;
        vi.spyOn(modalService, 'open').mockReturnValue(modalRef);

        component.displayClearDistributedDataModal();

        await modalRef.result;

        expect(mockBuildAgentsService.clearDistributedData).not.toHaveBeenCalled();
    });

    it('should navigate to job detail when jobId is provided', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        component.navigateToJobDetail('test-job-123');

        expect(navigateSpy).toHaveBeenCalledWith(['/admin/build-overview', 'test-job-123', 'job-details']);
    });

    it('should not navigate to job detail when jobId is undefined', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        component.navigateToJobDetail(undefined);

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should exclude PAUSED agents from build capacity calculation', () => {
        const agentsWithPaused: BuildAgentInformation[] = [
            {
                id: 1,
                buildAgent: { name: 'buildagent1', displayName: 'Build Agent 1', memberAddress: 'agent1' },
                maxNumberOfConcurrentBuildJobs: 2,
                numberOfCurrentBuildJobs: 1,
                status: BuildAgentStatus.ACTIVE,
            },
            {
                id: 2,
                buildAgent: { name: 'buildagent2', displayName: 'Build Agent 2', memberAddress: 'agent2' },
                maxNumberOfConcurrentBuildJobs: 3,
                numberOfCurrentBuildJobs: 0,
                status: BuildAgentStatus.PAUSED,
            },
        ];

        component.ngOnInit();
        websocketSubject.next(agentsWithPaused);

        expect(component.buildCapacity()).toBe(2); // Only active agent's capacity
        expect(component.currentBuilds()).toBe(1); // All current builds counted
    });

    it('should exclude SELF_PAUSED agents from build capacity calculation', () => {
        const agentsWithSelfPaused: BuildAgentInformation[] = [
            {
                id: 1,
                buildAgent: { name: 'buildagent1', displayName: 'Build Agent 1', memberAddress: 'agent1' },
                maxNumberOfConcurrentBuildJobs: 2,
                numberOfCurrentBuildJobs: 1,
                status: BuildAgentStatus.ACTIVE,
            },
            {
                id: 2,
                buildAgent: { name: 'buildagent2', displayName: 'Build Agent 2', memberAddress: 'agent2' },
                maxNumberOfConcurrentBuildJobs: 4,
                numberOfCurrentBuildJobs: 0,
                status: BuildAgentStatus.SELF_PAUSED,
            },
        ];

        component.ngOnInit();
        websocketSubject.next(agentsWithSelfPaused);

        expect(component.buildCapacity()).toBe(2); // Only active agent's capacity
    });

    it('should unsubscribe from initial load subscription on destroy', () => {
        mockBuildAgentsService.getBuildAgentSummary.mockReturnValue(of(mockBuildAgents));

        component.ngOnInit();
        const unsubscribeSpy = vi.spyOn(component.initialLoadSubscription!, 'unsubscribe');

        component.ngOnDestroy();

        expect(unsubscribeSpy).toHaveBeenCalled();
    });

    it('should handle agents with undefined numberOfCurrentBuildJobs', () => {
        const agentsWithUndefinedJobs: BuildAgentInformation[] = [
            {
                id: 1,
                buildAgent: { name: 'buildagent1', displayName: 'Build Agent 1', memberAddress: 'agent1' },
                maxNumberOfConcurrentBuildJobs: 2,
                numberOfCurrentBuildJobs: undefined,
                status: BuildAgentStatus.ACTIVE,
            },
        ];

        component.ngOnInit();
        websocketSubject.next(agentsWithUndefinedJobs);

        expect(component.currentBuilds()).toBe(0);
    });

    it('should handle agents with undefined maxNumberOfConcurrentBuildJobs', () => {
        const agentsWithUndefinedMax: BuildAgentInformation[] = [
            {
                id: 1,
                buildAgent: { name: 'buildagent1', displayName: 'Build Agent 1', memberAddress: 'agent1' },
                maxNumberOfConcurrentBuildJobs: undefined,
                numberOfCurrentBuildJobs: 1,
                status: BuildAgentStatus.ACTIVE,
            },
        ];

        component.ngOnInit();
        websocketSubject.next(agentsWithUndefinedMax);

        expect(component.buildCapacity()).toBe(0);
    });
});

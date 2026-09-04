import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BuildAgentSummaryComponent } from 'app/localci/build-agent-summary/build-agent-summary.component';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Subject, of, throwError } from 'rxjs';
import { BuildJob } from 'app/localci/shared/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { BuildAgentInformation, BuildAgentStatus } from 'app/localci/shared/entities/build-agent-information.model';
import { RepositoryInfo, TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { JobTimingInfo } from 'app/localci/shared/entities/job-timing-info.model';
import { BuildConfig } from 'app/localci/shared/entities/build-config.model';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BuildAgentsService } from 'app/localci/build-agents.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { BuildAgentPauseAllModalComponent } from 'app/localci/build-agent-summary/build-agent-pause-all-modal/build-agent-pause-all-modal.component';
import { BuildAgentClearDistributedDataComponent } from 'app/localci/build-agent-summary/build-agent-clear-distributed-data/build-agent-clear-distributed-data.component';

describe('BuildAgentSummaryComponent', () => {
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
            reservedGenerationSandboxSlots: 1,
            maxGenerationSandboxSlots: 3,
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

    beforeEach(async () => {
        TestBed.configureTestingModule({
            declarations: [],
            providers: [
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: BuildAgentsService, useValue: mockBuildAgentsService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
            ],
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(BuildAgentSummaryComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
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

    it('should render the active and maximum generation sandbox slot count per agent', () => {
        mockBuildAgentsService.getBuildAgentSummary.mockReturnValue(of(mockBuildAgents));
        component.ngOnInit();
        fixture.detectChanges();

        const text = fixture.nativeElement.textContent;
        expect(text).toContain('1 / 3');
        expect(text).toContain('—');
        expect(text).not.toContain('0 / 0');
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

    it('should aggregate generation sandbox capacity and usage', () => {
        component.buildAgents.set(mockBuildAgents);

        expect(component.generationSandboxSlots()).toEqual({ reserved: 1, maximum: 3 });
    });

    it('should present an agent with active sandboxes as active', () => {
        const sandboxOnlyAgent: BuildAgentInformation = {
            ...mockBuildAgents[0],
            status: BuildAgentStatus.IDLE,
            numberOfCurrentBuildJobs: 0,
            reservedGenerationSandboxSlots: 2,
        };

        expect(component.effectiveStatus(sandboxOnlyAgent)).toBe(BuildAgentStatus.ACTIVE);
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
        component.displayPauseBuildAgentModal();
        expect(component.pauseAllModalVisible()).toBeTruthy();

        component.displayClearDistributedDataModal();
        expect(component.clearDataModalVisible()).toBeTruthy();
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

    it('should call pauseAllBuildAgents when modal is confirmed', () => {
        mockBuildAgentsService.getBuildAgentSummary.mockReturnValue(of(mockBuildAgents));
        mockBuildAgentsService.pauseAllBuildAgents.mockReturnValue(of({}));
        fixture.detectChanges();

        const modal = fixture.debugElement.query(By.directive(BuildAgentPauseAllModalComponent)).componentInstance as BuildAgentPauseAllModalComponent;
        modal.confirmed.emit();

        expect(mockBuildAgentsService.pauseAllBuildAgents).toHaveBeenCalled();
    });

    it('should call clearDistributedData when modal is confirmed', () => {
        mockBuildAgentsService.getBuildAgentSummary.mockReturnValue(of(mockBuildAgents));
        mockBuildAgentsService.clearDistributedData.mockReturnValue(of({}));
        fixture.detectChanges();

        const modal = fixture.debugElement.query(By.directive(BuildAgentClearDistributedDataComponent)).componentInstance as BuildAgentClearDistributedDataComponent;
        modal.confirmed.emit();

        expect(mockBuildAgentsService.clearDistributedData).toHaveBeenCalled();
    });

    it('should not call pauseAllBuildAgents when modal is only opened', () => {
        mockBuildAgentsService.pauseAllBuildAgents.mockClear();

        component.displayPauseBuildAgentModal();

        expect(mockBuildAgentsService.pauseAllBuildAgents).not.toHaveBeenCalled();
    });

    it('should not call clearDistributedData when modal is only opened', () => {
        mockBuildAgentsService.clearDistributedData.mockClear();

        component.displayClearDistributedDataModal();

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

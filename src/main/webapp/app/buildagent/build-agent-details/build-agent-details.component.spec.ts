import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Subject, of, throwError } from 'rxjs';
import { BuildJob, FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { BuildAgentInformation, BuildAgentStatus } from 'app/buildagent/shared/entities/build-agent-information.model';
import { RepositoryInfo, TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { JobTimingInfo } from 'app/buildagent/shared/entities/job-timing-info.model';
import { BuildConfig } from 'app/buildagent/shared/entities/build-config.model';
import { BuildAgentDetailsComponent } from 'app/buildagent/build-agent-details/build-agent-details.component';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { FinishedBuildJobFilter } from 'app/buildagent/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import { BuildAgentsService } from 'app/buildagent/build-agents.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NO_ERRORS_SCHEMA } from '@angular/core';

describe('BuildAgentDetailsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildAgentDetailsComponent;
    let fixture: ComponentFixture<BuildAgentDetailsComponent>;
    let activatedRoute: MockActivatedRoute;
    let router: MockRouter;

    const mockWebsocketService = {
        subscribe: vi.fn(),
    };

    const mockBuildAgentsService = {
        getBuildAgentDetails: vi.fn().mockReturnValue(of([])),
        pauseBuildAgent: vi.fn().mockReturnValue(of({})),
        resumeBuildAgent: vi.fn().mockReturnValue(of({})),
        getFinishedBuildJobs: vi.fn().mockReturnValue(of({})),
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

    const mockBuildAgent: BuildAgentInformation = {
        id: 1,
        buildAgent: { name: 'agent1', memberAddress: 'localhost:8080', displayName: 'Agent 1' },
        maxNumberOfConcurrentBuildJobs: 2,
        numberOfCurrentBuildJobs: 2,
        status: BuildAgentStatus.ACTIVE,
    };

    const request = {
        page: 1,
        pageSize: 50,
        sortedColumn: 'buildSubmissionDate',
        sortingOrder: SortingOrder.DESCENDING,
        searchTerm: '',
    };

    const filterOptionsEmpty = {
        buildAgentAddress: 'localhost:8080',
        buildDurationFilterLowerBound: undefined,
        buildDurationFilterUpperBound: undefined,
        buildStartDateFilterFrom: undefined,
        buildStartDateFilterTo: undefined,
        status: undefined,
        appliedFilters: new Map<string, boolean>(),
        areDatesValid: true,
        areDurationFiltersValid: true,
        numberOfAppliedFilters: 0,
    };

    const mockFinishedJobs: FinishedBuildJob[] = [
        {
            id: '5',
            name: 'Build Job 5',
            buildAgentAddress: 'agent5',
            participationId: 105,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 1,
            repositoryName: 'repo5',
            repositoryType: 'USER',
            triggeredByPushTo: TriggeredByPushTo.USER,
            buildSubmissionDate: dayjs('2023-01-05'),
            buildStartDate: dayjs('2023-01-05'),
            buildCompletionDate: dayjs('2023-01-05'),
            buildDuration: '0.000s',
            commitHash: 'abc127',
        },
        {
            id: '6',
            name: 'Build Job 6',
            buildAgentAddress: 'agent6',
            participationId: 106,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 0,
            repositoryName: 'repo6',
            repositoryType: 'USER',
            triggeredByPushTo: TriggeredByPushTo.USER,
            buildStartDate: dayjs('2023-01-06'),
            buildCompletionDate: dayjs('2023-01-06'),
            buildDuration: '0.000s',
            commitHash: 'abc128',
        },
    ];

    const mockFinishedJobsResponse: HttpResponse<FinishedBuildJob[]> = new HttpResponse({ body: mockFinishedJobs });

    let alertService: AlertService;
    let alertServiceAddAlertStub: ReturnType<typeof vi.spyOn>;
    let modalService: NgbModal;
    const mockBuildQueueService = {
        getFinishedBuildJobs: vi.fn(),
        getRunningBuildJobs: vi.fn(),
        cancelBuildJob: vi.fn(),
        cancelAllRunningBuildJobsForAgent: vi.fn(),
    };

    let agentTopicSubject: Subject<BuildAgentInformation>;
    let runningJobsSubject: Subject<BuildJob[]>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [BuildAgentDetailsComponent],
            declarations: [],
            providers: [
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                { provide: BuildAgentsService, useValue: mockBuildAgentsService },
                { provide: BuildOverviewService, useValue: mockBuildQueueService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
            ],
            schemas: [NO_ERRORS_SCHEMA],
        }).overrideComponent(BuildAgentDetailsComponent, { set: { template: '' } });

        fixture = TestBed.createComponent(BuildAgentDetailsComponent);
        component = fixture.componentInstance;
        activatedRoute = TestBed.inject(ActivatedRoute) as MockActivatedRoute;
        modalService = TestBed.inject(NgbModal);
        router = TestBed.inject(Router) as unknown as MockRouter;
        activatedRoute.setParameters({ agentName: mockBuildAgent.buildAgent?.name });
        alertService = TestBed.inject(AlertService);
        alertServiceAddAlertStub = vi.spyOn(alertService, 'addAlert');

        agentTopicSubject = new Subject<BuildAgentInformation>();
        runningJobsSubject = new Subject<BuildJob[]>();
        mockWebsocketService.subscribe.mockImplementation((topic: string) => {
            if (topic === '/topic/admin/running-jobs') {
                return runningJobsSubject.asObservable();
            }
            if (topic === '/topic/admin/build-agent/' + mockBuildAgent.buildAgent?.name) {
                return agentTopicSubject.asObservable();
            }
            return new Subject().asObservable();
        });

        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs1));
        mockBuildQueueService.cancelBuildJob.mockReturnValue(of({}));
        mockBuildQueueService.cancelAllRunningBuildJobsForAgent.mockReturnValue(of({}));
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));
    });

    beforeEach(() => {
        vi.clearAllMocks();
        mockWebsocketService.subscribe.mockImplementation((topic: string) => {
            if (topic === '/topic/admin/running-jobs') {
                return runningJobsSubject.asObservable();
            }
            if (topic === '/topic/admin/build-agent/' + mockBuildAgent.buildAgent?.name) {
                return agentTopicSubject.asObservable();
            }
            return new Subject().asObservable();
        });
    });

    it('should load build agents on initialization', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();

        expect(mockBuildAgentsService.getBuildAgentDetails).toHaveBeenCalled();
        expect(component.buildAgent()).toEqual(mockBuildAgent);
        expect(mockBuildQueueService.getRunningBuildJobs).toHaveBeenCalledWith(mockBuildAgent.buildAgent?.name);
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalledWith(request, filterOptionsEmpty);
    });

    it('should initialize websocket subscription on initialization', () => {
        component.ngOnInit();

        expect(component.buildAgent()).toEqual(mockBuildAgent);
        expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/topic/admin/build-agent/' + component.buildAgent()?.buildAgent?.name);
        expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/topic/admin/running-jobs');
    });

    it('should unsubscribe from the websocket channel on destruction', () => {
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        const agentUnsubscribeSpy = vi.spyOn(component.agentDetailsWebsocketSubscription!, 'unsubscribe');
        const runningUnsubscribeSpy = vi.spyOn(component.runningJobsWebsocketSubscription!, 'unsubscribe');

        component.ngOnDestroy();

        expect(agentUnsubscribeSpy).toHaveBeenCalled();
        expect(runningUnsubscribeSpy).toHaveBeenCalled();
    });

    it('should cancel a build job', () => {
        const buildJob = mockRunningJobs1[0];
        const spy = vi.spyOn(component, 'cancelBuildJob');

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        component.cancelBuildJob(buildJob.id!);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(buildJob.id!);
    });

    it('should cancel all build jobs of a build agent', () => {
        const spy = vi.spyOn(component, 'cancelAllBuildJobs');

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        component.cancelAllBuildJobs();

        expect(spy).toHaveBeenCalledOnce();
    });

    it('should show an alert when pausing build agent without a name', () => {
        component.buildAgent.set({ ...mockBuildAgent, buildAgent: { ...mockBuildAgent.buildAgent, name: '' } });
        component.pauseBuildAgent();

        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.WARNING,
            message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName',
        });
    });

    it('should show an alert when resuming build agent without a name', () => {
        component.buildAgent.set({ ...mockBuildAgent, buildAgent: { ...mockBuildAgent.buildAgent, name: '' } });
        component.resumeBuildAgent();

        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.WARNING,
            message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName',
        });
    });

    it('should show success alert when pausing build agent', () => {
        component.buildAgent.set(mockBuildAgent);
        fixture.changeDetectorRef.detectChanges();

        component.pauseBuildAgent();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.SUCCESS,
            message: 'artemisApp.buildAgents.alerts.buildAgentPaused',
        });
    });

    it('should show error alert when pausing build agent fails', () => {
        mockBuildAgentsService.pauseBuildAgent.mockReturnValue(throwError(() => new Error()));
        component.buildAgent.set(mockBuildAgent);
        fixture.changeDetectorRef.detectChanges();

        component.pauseBuildAgent();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'artemisApp.buildAgents.alerts.buildAgentPauseFailed',
        });
    });

    it('should show success alert when resuming build agent', () => {
        component.buildAgent.set(mockBuildAgent);
        fixture.changeDetectorRef.detectChanges();

        component.resumeBuildAgent();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.SUCCESS,
            message: 'artemisApp.buildAgents.alerts.buildAgentResumed',
        });
    });

    it('should show error alert when resuming build agent fails', () => {
        mockBuildAgentsService.resumeBuildAgent.mockReturnValue(throwError(() => new Error()));
        component.buildAgent.set(mockBuildAgent);
        fixture.changeDetectorRef.detectChanges();

        component.resumeBuildAgent();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'artemisApp.buildAgents.alerts.buildAgentResumeFailed',
        });
    });

    it('should trigger refresh on search term change', async () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        component.searchTerm = 'search';
        fixture.changeDetectorRef.detectChanges();
        component.triggerLoadFinishedJobs();

        const requestWithSearchTerm = { ...request };
        requestWithSearchTerm.searchTerm = 'search';
        await new Promise((resolve) => setTimeout(resolve, 110));
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenLastCalledWith(requestWithSearchTerm, expect.objectContaining({ buildAgentAddress: 'localhost:8080' }));
    });

    it('should set build job duration', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(component.finishedBuildJobs()).toEqual(mockFinishedJobs);
        for (const finishedBuildJob of component.finishedBuildJobs()) {
            const { buildDuration, buildCompletionDate, buildStartDate } = finishedBuildJob;
            if (buildDuration && buildCompletionDate && buildStartDate) {
                expect(buildDuration).toEqual((buildCompletionDate.diff(buildStartDate, 'milliseconds') / 1000).toFixed(3) + 's');
            }
        }
    });

    it('should correctly set filterModal values', () => {
        const modalRef = {
            componentInstance: {
                finishedBuildJobFilter: undefined,
                buildAgentAddress: undefined,
                finishedBuildJobs: undefined,
            },
            result: Promise.resolve('close'),
        } as NgbModalRef;
        const openSpy = vi.spyOn(modalService, 'open').mockReturnValue(modalRef);
        component.finishedBuildJobs.set(mockFinishedJobs);
        component.buildAgent.set(mockBuildAgent);
        component.finishedBuildJobFilter = new FinishedBuildJobFilter(mockBuildAgent.buildAgent!.memberAddress!);
        fixture.changeDetectorRef.detectChanges();

        component.openFilterModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(modalRef.componentInstance.finishedBuildJobFilter).toEqual(filterOptionsEmpty);
        expect(modalRef.componentInstance.buildAgentAddress).toEqual(mockBuildAgent.buildAgent?.memberAddress);
        expect(modalRef.componentInstance.finishedBuildJobs).toEqual(component.finishedBuildJobs());
    });

    it('should correctly open build log', () => {
        const windowSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        component.viewBuildLogs('1');
        expect(windowSpy).toHaveBeenCalledOnce();
        expect(windowSpy).toHaveBeenCalledWith('/api/programming/build-log/1', '_blank');
    });

    it('should navigate to job detail page', () => {
        const navigateSpy = vi.spyOn(router, 'navigate');
        component.navigateToJobDetail('job-123');
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/admin', 'build-overview', 'job-123', 'job-details']);
    });

    it('should set agentNotFound to true when receiving 404 error', () => {
        const notFoundError = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(throwError(() => notFoundError));

        component.ngOnInit();

        expect(component.agentNotFound()).toBe(true);
        // Should still load finished jobs even on 404
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalled();
    });

    it('should call onError for non-404 errors and not set agentNotFound', () => {
        const serverError = new HttpErrorResponse({ status: 500, statusText: 'Internal Server Error' });
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(throwError(() => serverError));

        component.ngOnInit();

        expect(component.agentNotFound()).toBe(false);
        // onError should have been triggered via alertService
        // The component still loads finished jobs even on error
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalled();
    });

    it('should reset agentNotFound when receiving valid agent data after 404', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        // First, simulate a 404 to set agentNotFound
        component.agentNotFound.set(true);

        component.ngOnInit();

        // After successful load, agentNotFound should be reset to false
        expect(component.agentNotFound()).toBe(false);
    });

    it('should use query param (agentName) directly for filtering when agent lookup returns 404', () => {
        const notFoundError = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(throwError(() => notFoundError));

        // Set the agentName to simulate navigation from finished jobs with an address
        component.agentName = '[127.0.0.1]:8080';

        component.loadAgentData();

        // When 404, the component should use the query param (agentName) directly for filtering
        expect(component.finishedBuildJobFilter.buildAgentAddress).toBe('[127.0.0.1]:8080');
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalled();
    });

    it('should re-subscribe to WebSocket when agent name differs from query param (address-based navigation)', () => {
        // Simulate navigation by address - the query param contains an address, not a name
        const addressBasedAgent: BuildAgentInformation = {
            ...mockBuildAgent,
            buildAgent: { name: 'actual-agent-name', memberAddress: '[127.0.0.1]:8080', displayName: 'Agent 1' },
        };
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(addressBasedAgent));

        // Set initial agentName to the address (simulating navigation from finished jobs)
        activatedRoute.setParameters({ agentName: '[127.0.0.1]:8080' });
        component.ngOnInit();

        // After loading, agentName should be updated to the actual name
        expect(component.agentName).toBe('actual-agent-name');
        // WebSocket should be re-subscribed with the correct channel
        expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/topic/admin/build-agent/actual-agent-name');
    });

    it('should not trigger search if search term is less than 3 characters', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        const finishedJobsSearchTriggerSpy = vi.spyOn(component.finishedJobsSearchTrigger, 'next');

        // Set search term to 2 characters (below threshold)
        component.searchTerm = 'ab';
        component.triggerLoadFinishedJobs();

        expect(finishedJobsSearchTriggerSpy).not.toHaveBeenCalled();
    });

    it('should trigger search if search term is empty', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        const finishedJobsSearchTriggerSpy = vi.spyOn(component.finishedJobsSearchTrigger, 'next');

        // Set search term to empty (should trigger)
        component.searchTerm = '';
        component.triggerLoadFinishedJobs();

        expect(finishedJobsSearchTriggerSpy).toHaveBeenCalled();
    });

    it('should trigger search if search term is undefined', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        const finishedJobsSearchTriggerSpy = vi.spyOn(component.finishedJobsSearchTrigger, 'next');

        // Set search term to undefined (should trigger)
        component.searchTerm = undefined;
        component.triggerLoadFinishedJobs();

        expect(finishedJobsSearchTriggerSpy).toHaveBeenCalled();
    });

    it('should update page and reload finished jobs on page change', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        vi.clearAllMocks();
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

        component.onPageChange({ page: 2, pageSize: 50, direction: 'next' });

        expect(component.currentPage).toBe(2);
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalled();
    });

    it('should not update page if page number is undefined', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        const initialPage = component.currentPage;

        component.onPageChange({} as any);

        expect(component.currentPage).toBe(initialPage);
    });

    it('should not cancel all build jobs if agent name is missing', () => {
        component.buildAgent.set({ ...mockBuildAgent, buildAgent: undefined });

        component.cancelAllBuildJobs();

        expect(mockBuildQueueService.cancelAllRunningBuildJobsForAgent).not.toHaveBeenCalled();
    });

    it('should update running jobs when websocket receives jobs for this agent', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        component.ngOnInit();

        // Emit running jobs via websocket that match the current agent
        const agentJobs: BuildJob[] = [
            {
                ...mockRunningJobs1[0],
                buildAgent: { name: mockBuildAgent.buildAgent?.name, memberAddress: 'localhost:8080', displayName: 'Agent 1' },
            },
        ];
        runningJobsSubject.next(agentJobs);

        expect(component.runningBuildJobs().length).toBe(1);
    });

    it('should clear running jobs when websocket receives no jobs for this agent', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        component.ngOnInit();

        // Initially set some running jobs
        component.runningBuildJobs.set(mockRunningJobs1);

        // Emit running jobs via websocket that do not match the current agent
        const otherAgentJobs: BuildJob[] = [
            {
                ...mockRunningJobs1[0],
                buildAgent: { name: 'other-agent', memberAddress: 'localhost:8081', displayName: 'Other Agent' },
            },
        ];
        runningJobsSubject.next(otherAgentJobs);

        expect(component.runningBuildJobs()).toEqual([]);
    });

    it('should handle error when loading finished build jobs', async () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(component.isLoading()).toBe(false);
    });

    it('should handle error in debounced search subscription', async () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        // Clear mocks and set up error response for debounced search
        vi.clearAllMocks();
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        // Trigger the debounced search
        component.searchTerm = 'test';
        component.triggerLoadFinishedJobs();

        // Wait for debounce
        await new Promise((resolve) => setTimeout(resolve, 110));

        expect(component.isLoading()).toBe(false);
    });

    it('should apply filter from modal result', async () => {
        const newFilter = new FinishedBuildJobFilter('new-address');
        const modalRef = {
            componentInstance: {
                finishedBuildJobFilter: undefined,
                buildAgentAddress: undefined,
                finishedBuildJobs: undefined,
            },
            result: Promise.resolve(newFilter),
        } as NgbModalRef;
        vi.spyOn(modalService, 'open').mockReturnValue(modalRef);

        component.buildAgent.set(mockBuildAgent);
        component.finishedBuildJobFilter = new FinishedBuildJobFilter(mockBuildAgent.buildAgent!.memberAddress!);
        fixture.changeDetectorRef.detectChanges();

        component.openFilterModal();

        await modalRef.result;

        expect(component.finishedBuildJobFilter).toEqual(newFilter);
    });

    it('should handle modal dismissal gracefully', async () => {
        const modalRef = {
            componentInstance: {
                finishedBuildJobFilter: undefined,
                buildAgentAddress: undefined,
                finishedBuildJobs: undefined,
            },
            result: Promise.reject('dismissed'),
        } as NgbModalRef;
        vi.spyOn(modalService, 'open').mockReturnValue(modalRef);

        component.buildAgent.set(mockBuildAgent);
        component.finishedBuildJobFilter = new FinishedBuildJobFilter(mockBuildAgent.buildAgent!.memberAddress!);
        fixture.changeDetectorRef.detectChanges();

        // Should not throw
        component.openFilterModal();

        await new Promise((resolve) => setTimeout(resolve, 10));
        // Filter should remain unchanged
        expect(component.finishedBuildJobFilter.buildAgentAddress).toBe(mockBuildAgent.buildAgent!.memberAddress);
    });

    it('should update build agent when websocket receives agent update', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        component.ngOnInit();

        const updatedAgent: BuildAgentInformation = {
            ...mockBuildAgent,
            numberOfCurrentBuildJobs: 1,
            buildAgentDetails: {
                successfulBuilds: 10,
                failedBuilds: 2,
                cancelledBuilds: 1,
                timedOutBuild: 0,
                totalBuilds: 13,
            },
        };

        agentTopicSubject.next(updatedAgent);

        expect(component.buildAgent()).toEqual(updatedAgent);
        expect(component.buildJobStatistics().successfulBuilds).toBe(10);
        expect(component.buildJobStatistics().failedBuilds).toBe(2);
        expect(component.buildJobStatistics().cancelledBuilds).toBe(1);
        expect(component.buildJobStatistics().timeOutBuilds).toBe(0);
        expect(component.buildJobStatistics().totalBuilds).toBe(13);
    });

    it('should handle build job without start date in duration calculation', () => {
        const jobsWithoutStartDate: BuildJob[] = [
            {
                ...mockRunningJobs1[0],
                jobTimingInfo: undefined,
            },
        ];

        const result = component.updateBuildJobDuration(jobsWithoutStartDate);

        expect(result).toHaveLength(1);
        expect(result[0].jobTimingInfo).toBeUndefined();
    });

    it('should calculate duration for finished build jobs without dates', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        const jobsWithoutDates: FinishedBuildJob[] = [
            {
                ...mockFinishedJobs[0],
                buildStartDate: undefined,
                buildCompletionDate: undefined,
            },
        ];
        const responseWithoutDates = new HttpResponse({ body: jobsWithoutDates });
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(responseWithoutDates));

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        // Should not crash, and duration should not be calculated
        expect(component.finishedBuildJobs()).toHaveLength(1);
    });

    it('should set hasMore based on response headers', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        const headers = new HttpHeaders().set('x-has-next', 'true');
        const responseWithHeaders = new HttpResponse({ body: mockFinishedJobs, headers });
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(responseWithHeaders));

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(component.hasMore()).toBe(true);
    });

    it('should set hasMore to false when x-has-next is false', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));

        const headers = new HttpHeaders().set('x-has-next', 'false');
        const responseWithHeaders = new HttpResponse({ body: mockFinishedJobs, headers });
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(responseWithHeaders));

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(component.hasMore()).toBe(false);
    });
});

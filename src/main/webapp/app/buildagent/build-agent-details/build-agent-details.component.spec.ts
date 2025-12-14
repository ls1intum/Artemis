import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Subject, of, throwError } from 'rxjs';
import { BuildJob, FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { MockProvider } from 'ng-mocks';
import { BuildAgentInformation, BuildAgentStatus } from 'app/buildagent/shared/entities/build-agent-information.model';
import { RepositoryInfo, TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { JobTimingInfo } from 'app/buildagent/shared/entities/job-timing-info.model';
import { BuildConfig } from 'app/buildagent/shared/entities/build-config.model';
import { BuildAgentDetailsComponent } from 'app/buildagent/build-agent-details/build-agent-details.component';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
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
    let component: BuildAgentDetailsComponent;
    let fixture: ComponentFixture<BuildAgentDetailsComponent>;
    let activatedRoute: MockActivatedRoute;

    const mockWebsocketService = {
        subscribe: jest.fn(),
    };

    const mockBuildAgentsService = {
        getBuildAgentDetails: jest.fn().mockReturnValue(of([])),
        pauseBuildAgent: jest.fn().mockReturnValue(of({})),
        resumeBuildAgent: jest.fn().mockReturnValue(of({})),
        getFinishedBuildJobs: jest.fn().mockReturnValue(of({})),
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
            buildDuration: undefined,
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
            buildDuration: undefined,
            commitHash: 'abc128',
        },
    ];

    const mockFinishedJobsResponse: HttpResponse<FinishedBuildJob[]> = new HttpResponse({ body: mockFinishedJobs });

    let alertService: AlertService;
    let alertServiceAddAlertStub: jest.SpyInstance;
    let modalService: NgbModal;
    const mockBuildQueueService = {
        getFinishedBuildJobs: jest.fn(),
        getRunningBuildJobs: jest.fn(),
        cancelBuildJob: jest.fn(),
        cancelAllRunningBuildJobsForAgent: jest.fn(),
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
                { provide: DataTableComponent, useClass: DataTableComponent },
                { provide: BuildOverviewService, useValue: mockBuildQueueService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
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
        activatedRoute.setParameters({ agentName: mockBuildAgent.buildAgent?.name });
        alertService = TestBed.inject(AlertService);
        alertServiceAddAlertStub = jest.spyOn(alertService, 'addAlert');

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
        jest.clearAllMocks();
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
        expect(component.buildAgent).toEqual(mockBuildAgent);
        expect(mockBuildQueueService.getRunningBuildJobs).toHaveBeenCalledWith(mockBuildAgent.buildAgent?.name);
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalledWith(request, filterOptionsEmpty);
    });

    it('should initialize websocket subscription on initialization', () => {
        component.ngOnInit();

        expect(component.buildAgent).toEqual(mockBuildAgent);
        expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/topic/admin/build-agent/' + component.buildAgent.buildAgent?.name);
        expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/topic/admin/running-jobs');
    });

    it('should unsubscribe from the websocket channel on destruction', () => {
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        const agentUnsubscribeSpy = jest.spyOn(component.agentDetailsWebsocketSubscription!, 'unsubscribe');
        const runningUnsubscribeSpy = jest.spyOn(component.runningJobsWebsocketSubscription!, 'unsubscribe');

        component.ngOnDestroy();

        expect(agentUnsubscribeSpy).toHaveBeenCalled();
        expect(runningUnsubscribeSpy).toHaveBeenCalled();
    });

    it('should cancel a build job', () => {
        const buildJob = mockRunningJobs1[0];
        const spy = jest.spyOn(component, 'cancelBuildJob');

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        component.cancelBuildJob(buildJob.id!);

        expect(spy).toHaveBeenCalledExactlyOnceWith(buildJob.id!);
    });

    it('should cancel all build jobs of a build agent', () => {
        const spy = jest.spyOn(component, 'cancelAllBuildJobs');

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        component.cancelAllBuildJobs();

        expect(spy).toHaveBeenCalledOnce();
    });

    it('should show an alert when pausing build agent without a name', () => {
        component.buildAgent = { ...mockBuildAgent, buildAgent: { ...mockBuildAgent.buildAgent, name: '' } };
        component.pauseBuildAgent();

        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.WARNING,
            message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName',
        });
    });

    it('should show an alert when resuming build agent without a name', () => {
        component.buildAgent = { ...mockBuildAgent, buildAgent: { ...mockBuildAgent.buildAgent, name: '' } };
        component.resumeBuildAgent();

        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.WARNING,
            message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName',
        });
    });

    it('should show success alert when pausing build agent', () => {
        component.buildAgent = mockBuildAgent;
        fixture.changeDetectorRef.detectChanges();

        component.pauseBuildAgent();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.SUCCESS,
            message: 'artemisApp.buildAgents.alerts.buildAgentPaused',
        });
    });

    it('should show error alert when pausing build agent fails', () => {
        mockBuildAgentsService.pauseBuildAgent.mockReturnValue(throwError(() => new Error()));
        component.buildAgent = mockBuildAgent;
        fixture.changeDetectorRef.detectChanges();

        component.pauseBuildAgent();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'artemisApp.buildAgents.alerts.buildAgentPauseFailed',
        });
    });

    it('should show success alert when resuming build agent', () => {
        component.buildAgent = mockBuildAgent;
        fixture.changeDetectorRef.detectChanges();

        component.resumeBuildAgent();
        expect(alertServiceAddAlertStub).toHaveBeenCalledWith({
            type: AlertType.SUCCESS,
            message: 'artemisApp.buildAgents.alerts.buildAgentResumed',
        });
    });

    it('should show error alert when resuming build agent fails', () => {
        mockBuildAgentsService.resumeBuildAgent.mockReturnValue(throwError(() => new Error()));
        component.buildAgent = mockBuildAgent;
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

        expect(component.finishedBuildJobs).toEqual(mockFinishedJobs);
        for (const finishedBuildJob of component.finishedBuildJobs) {
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
        const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(modalRef);
        component.finishedBuildJobs = mockFinishedJobs;
        component.buildAgent = mockBuildAgent;
        component.finishedBuildJobFilter = new FinishedBuildJobFilter(mockBuildAgent.buildAgent!.memberAddress!);
        fixture.changeDetectorRef.detectChanges();

        component.openFilterModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(modalRef.componentInstance.finishedBuildJobFilter).toEqual(filterOptionsEmpty);
        expect(modalRef.componentInstance.buildAgentAddress).toEqual(mockBuildAgent.buildAgent?.memberAddress);
        expect(modalRef.componentInstance.finishedBuildJobs).toEqual(component.finishedBuildJobs);
    });

    it('should correctly open build log', () => {
        const windowSpy = jest.spyOn(window, 'open').mockImplementation();
        component.viewBuildLogs('1');
        expect(windowSpy).toHaveBeenCalledOnce();
        expect(windowSpy).toHaveBeenCalledWith('/api/programming/build-log/1', '_blank');
    });
});

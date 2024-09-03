import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { of } from 'rxjs';
import { BuildJob } from 'app/entities/programming/build-job.model';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { BuildAgent } from 'app/entities/programming/build-agent.model';
import { RepositoryInfo, TriggeredByPushTo } from 'app/entities/programming/repository-info.model';
import { JobTimingInfo } from 'app/entities/job-timing-info.model';
import { BuildConfig } from 'app/entities/programming/build-config.model';
import { BuildAgentDetailsComponent } from 'app/localci/build-agents/build-agent-details/build-agent-details/build-agent-details.component';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

describe('BuildAgentDetailsComponent', () => {
    let component: BuildAgentDetailsComponent;
    let fixture: ComponentFixture<BuildAgentDetailsComponent>;
    let activatedRoute: MockActivatedRoute;

    const mockWebsocketService = {
        subscribe: jest.fn(),
        unsubscribe: jest.fn(),
        receive: jest.fn().mockReturnValue(of([])),
    };

    const mockBuildAgentsService = {
        getBuildAgentDetails: jest.fn().mockReturnValue(of([])),
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
        testwiseCoverageEnabled: false,
        resultPaths: [],
    };

    const mockRunningJobs1: BuildJob[] = [
        {
            id: '2',
            name: 'Build Job 2',
            buildAgentAddress: 'agent2',
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
            buildAgentAddress: 'agent4',
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

    const mockRecentBuildJobs1: BuildJob[] = [
        {
            id: '1',
            name: 'Build Job 1',
            buildAgentAddress: 'agent1',
            participationId: 101,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 4,
            repositoryInfo: repositoryInfo,
            jobTimingInfo: jobTimingInfo1,
            buildConfig: buildConfig,
        },
    ];

    const mockBuildAgent: BuildAgent = {
        id: 1,
        name: 'buildagent1',
        maxNumberOfConcurrentBuildJobs: 2,
        numberOfCurrentBuildJobs: 2,
        runningBuildJobs: mockRunningJobs1,
        recentBuildJobs: mockRecentBuildJobs1,
        status: true,
    };

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule],
            declarations: [BuildAgentDetailsComponent, MockPipe(ArtemisTranslatePipe), MockComponent(DataTableComponent)],
            providers: [
                { provide: JhiWebsocketService, useValue: mockWebsocketService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                { provide: BuildAgentsService, useValue: mockBuildAgentsService },
                { provide: DataTableComponent, useClass: DataTableComponent },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentDetailsComponent);
        component = fixture.componentInstance;
        activatedRoute = fixture.debugElement.injector.get(ActivatedRoute) as MockActivatedRoute;
        activatedRoute.setParameters({ agentName: mockBuildAgent.name });
    }));

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('should load build agents on initialization', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        mockWebsocketService.receive.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();

        expect(mockBuildAgentsService.getBuildAgentDetails).toHaveBeenCalled();
        expect(component.buildAgent).toEqual(mockBuildAgent);
    });

    it('should initialize websocket subscription on initialization', () => {
        mockWebsocketService.receive.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();

        expect(component.buildAgent).toEqual(mockBuildAgent);
        expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/topic/admin/build-agent/' + component.buildAgent.name);
        expect(mockWebsocketService.receive).toHaveBeenCalledWith('/topic/admin/build-agent/' + component.buildAgent.name);
    });

    it('should unsubscribe from the websocket channel on destruction', () => {
        mockWebsocketService.receive.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();

        component.ngOnDestroy();

        expect(mockWebsocketService.unsubscribe).toHaveBeenCalledWith('/topic/admin/build-agent/' + component.buildAgent.name);
    });

    it('should set recent build jobs duration', () => {
        mockBuildAgentsService.getBuildAgentDetails.mockReturnValue(of(mockBuildAgent));
        mockWebsocketService.receive.mockReturnValue(of(mockBuildAgent));

        component.ngOnInit();

        for (const recentBuildJob of component.buildAgent.recentBuildJobs || []) {
            const { jobTimingInfo } = recentBuildJob;
            const { buildCompletionDate, buildStartDate, buildDuration } = jobTimingInfo || {};
            if (buildDuration && jobTimingInfo) {
                expect(buildDuration).toEqual(buildCompletionDate!.diff(buildStartDate!, 'milliseconds') / 1000);
            }
        }
    });

    it('should cancel a build job', () => {
        const buildJob = mockRunningJobs1[0];
        const spy = jest.spyOn(component, 'cancelBuildJob');

        component.ngOnInit();
        component.cancelBuildJob(buildJob.id!);

        expect(spy).toHaveBeenCalledExactlyOnceWith(buildJob.id!);
    });

    it('should cancel all build jobs of a build agent', () => {
        const spy = jest.spyOn(component, 'cancelAllBuildJobs');

        component.ngOnInit();
        component.cancelAllBuildJobs();

        expect(spy).toHaveBeenCalledOnce();
    });
});

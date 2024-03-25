import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { BuildAgentsComponent } from 'app/localci/build-agents/build-agents.component';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildAgentsService } from 'app/localci/build-agents/build-agents.service';
import { of } from 'rxjs';
import { BuildJob } from 'app/entities/build-job.model';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { BuildAgent } from 'app/entities/build-agent.model';
import { RepositoryInfo, TriggeredByPushTo } from 'app/entities/repository-info.model';
import { JobTimingInfo } from 'app/entities/job-timing-info.model';
import { BuildConfig } from 'app/entities/build-config.model';

describe('BuildAgentsComponent', () => {
    let component: BuildAgentsComponent;
    let fixture: ComponentFixture<BuildAgentsComponent>;

    const mockWebsocketService = {
        subscribe: jest.fn(),
        unsubscribe: jest.fn(),
        receive: jest.fn().mockReturnValue(of([])),
    };

    const mockBuildAgentsService = {
        getBuildAgents: jest.fn().mockReturnValue(of([])),
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

    const jobTimingInfo2: JobTimingInfo = {
        submissionDate: dayjs('2023-01-03'),
        buildStartDate: dayjs('2023-01-03'),
        buildCompletionDate: dayjs('2023-01-07'),
        buildDuration: undefined,
    };

    const buildConfig: BuildConfig = {
        dockerImage: 'someImage',
        commitHash: 'abc124',
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

    const mockRunningJobs2: BuildJob[] = [
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
        {
            id: '3',
            name: 'Build Job 3',
            buildAgentAddress: 'agent3',
            participationId: 103,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 5,
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
            jobTimingInfo: jobTimingInfo2,
            buildConfig: buildConfig,
        },
    ];

    const mockRecentBuildJobs2: BuildJob[] = [
        {
            id: '3',
            name: 'Build Job 3',
            buildAgentAddress: 'agent3',
            participationId: 103,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 5,
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
            jobTimingInfo: jobTimingInfo2,
            buildConfig: buildConfig,
        },
    ];

    const mockBuildAgents: BuildAgent[] = [
        {
            id: 1,
            name: 'buildagent1',
            maxNumberOfConcurrentBuildJobs: 2,
            numberOfCurrentBuildJobs: 2,
            runningBuildJobs: mockRunningJobs1,
            recentBuildJobs: mockRecentBuildJobs1,
            status: true,
        },
        {
            id: 2,
            name: 'buildagent2',
            maxNumberOfConcurrentBuildJobs: 2,
            numberOfCurrentBuildJobs: 2,
            runningBuildJobs: mockRunningJobs2,
            recentBuildJobs: mockRecentBuildJobs2,
            status: true,
        },
    ];

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule],
            declarations: [BuildAgentsComponent, MockPipe(ArtemisTranslatePipe), MockComponent(DataTableComponent)],
            providers: [
                { provide: JhiWebsocketService, useValue: mockWebsocketService },
                { provide: BuildAgentsService, useValue: mockBuildAgentsService },
                { provide: DataTableComponent, useClass: DataTableComponent },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(BuildAgentsComponent);
        component = fixture.componentInstance;
    }));

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load build agents on initialization', () => {
        mockBuildAgentsService.getBuildAgents.mockReturnValue(of(mockBuildAgents));
        mockWebsocketService.receive.mockReturnValue(of(mockBuildAgents));

        component.ngOnInit();

        expect(mockBuildAgentsService.getBuildAgents).toHaveBeenCalled();
        expect(component.buildAgents).toEqual(mockBuildAgents);
    });

    it('should initialize websocket subscription on initialization', () => {
        mockWebsocketService.receive.mockReturnValue(of(mockBuildAgents));

        component.ngOnInit();

        expect(component.buildAgents).toEqual(mockBuildAgents);
        expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/topic/admin/build-agents');
        expect(mockWebsocketService.receive).toHaveBeenCalledWith('/topic/admin/build-agents');
    });

    it('should unsubscribe from the websocket channel on destruction', () => {
        component.ngOnDestroy();

        expect(mockWebsocketService.unsubscribe).toHaveBeenCalledWith('/topic/admin/build-agents');
    });

    it('should set recent build jobs duration', () => {
        mockBuildAgentsService.getBuildAgents.mockReturnValue(of(mockBuildAgents));
        mockWebsocketService.receive.mockReturnValue(of(mockBuildAgents));

        component.ngOnInit();

        for (const buildAgent of component.buildAgents) {
            for (const recentBuildJob of buildAgent.recentBuildJobs || []) {
                const { jobTimingInfo } = recentBuildJob;
                const { buildCompletionDate, buildStartDate, buildDuration } = jobTimingInfo || {};
                if (buildDuration && jobTimingInfo) {
                    expect(buildDuration).toEqual(buildCompletionDate!.diff(buildStartDate!, 'milliseconds') / 1000);
                }
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
        const buildAgent = mockBuildAgents[0];
        const spy = jest.spyOn(component, 'cancelAllBuildJobs');

        component.ngOnInit();
        component.cancelAllBuildJobs(buildAgent.name!);

        expect(spy).toHaveBeenCalledExactlyOnceWith(buildAgent.name!);
    });
});

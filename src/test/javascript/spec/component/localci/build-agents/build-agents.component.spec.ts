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

    const mockRunningJobs1: BuildJob[] = [
        {
            id: '2',
            name: 'Build Job 2',
            participationId: 102,
            repositoryTypeOrUserName: 'repo2',
            commitHash: 'abc124',
            submissionDate: dayjs('2023-01-01'),
            retryCount: 2,
            buildStartDate: dayjs('2023-01-01'),
            priority: 5,
            courseId: 10,
            isPushToTestRepository: false,
        },
        {
            id: '4',
            name: 'Build Job 4',
            participationId: 104,
            repositoryTypeOrUserName: 'repo4',
            commitHash: 'abc126',
            submissionDate: dayjs('2023-01-04'),
            retryCount: 3,
            buildStartDate: dayjs('2023-01-04'),
            priority: 2,
            courseId: 10,
            isPushToTestRepository: false,
        },
    ];

    const mockRunningJobs2 = [
        {
            id: 1,
            name: 'Build Job 1',
            participationId: 101,
            repositoryTypeOrUserName: 'repo1',
            commitHash: 'abc123',
            submissionDate: dayjs('2023-01-02'),
            retryCount: 2,
            buildStartDate: null,
            priority: 5,
            courseId: 10,
            isPushToTestRepository: false,
        },
        {
            id: 3,
            name: 'Build Job 3',
            participationId: 103,
            repositoryTypeOrUserName: 'repo3',
            commitHash: 'abc125',
            submissionDate: dayjs('2023-01-03'),
            retryCount: 1,
            buildStartDate: null,
            priority: 3,
            courseId: 10,
            isPushToTestRepository: false,
        },
    ];

    const mockBuildAgents = [
        {
            id: 1,
            name: 'buildagent1',
            maxNumberOfConcurrentBuildJobs: 2,
            numberOfCurrentBuildJobs: 2,
            runningBuildJobs: mockRunningJobs1,
            status: true,
        },
        {
            id: 2,
            name: 'buildagent2',
            maxNumberOfConcurrentBuildJobs: 2,
            numberOfCurrentBuildJobs: 2,
            runningBuildJobs: mockRunningJobs2,
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

    it('should get build job IDs', () => {
        const result = component.getBuildJobIds(mockRunningJobs1);

        expect(result).toBe('2, 4');
    });

    it('should return an empty string for no build jobs', () => {
        const result = component.getBuildJobIds([]);

        expect(result).toBe('');
    });
});

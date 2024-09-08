import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { BuildQueueComponent, FinishedBuildJobFilter, FinishedBuildJobFilterStorageKey } from 'app/localci/build-queue/build-queue.component';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisTestModule } from '../../../test.module';
import { BuildJobStatistics, FinishedBuildJob, SpanType } from 'app/entities/programming/build-job.model';
import { TriggeredByPushTo } from 'app/entities/programming/repository-info.model';
import { waitForAsync } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { LocalStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { PieChartModule } from '@swimlane/ngx-charts';

describe('BuildQueueComponent', () => {
    let component: BuildQueueComponent;
    let fixture: ComponentFixture<BuildQueueComponent>;
    let mockActivatedRoute: any;

    const mockBuildQueueService = {
        getQueuedBuildJobsByCourseId: jest.fn(),
        getRunningBuildJobsByCourseId: jest.fn(),
        getQueuedBuildJobs: jest.fn(),
        getRunningBuildJobs: jest.fn(),
        cancelBuildJobInCourse: jest.fn(),
        cancelBuildJob: jest.fn(),
        cancelAllQueuedBuildJobsInCourse: jest.fn(),
        cancelAllRunningBuildJobsInCourse: jest.fn(),
        cancelAllQueuedBuildJobs: jest.fn(),
        cancelAllRunningBuildJobs: jest.fn(),
        getFinishedBuildJobsByCourseId: jest.fn(),
        getFinishedBuildJobs: jest.fn(),
        getBuildJobStatistics: jest.fn(),
        getBuildJobStatisticsForCourse: jest.fn(),
    };

    const mockLocalStorageService = new MockLocalStorageService();
    mockLocalStorageService.clear = (key?: string) => {
        if (key) {
            if (key in mockLocalStorageService.storage) {
                delete mockLocalStorageService.storage[key];
            }
        } else {
            mockLocalStorageService.storage = {};
        }
    };

    const accountServiceMock = { identity: jest.fn(), getAuthenticationState: jest.fn() };

    const testCourseId = 123;
    const mockQueuedJobs = [
        {
            id: '1',
            name: 'Build Job 1',
            buildAgentAddress: 'agent1',
            participationId: 101,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 4,
            repositoryInfo: {
                repositoryName: 'repo1',
                repositoryType: 'USER',
                triggeredByPushTo: 'USER',
                assignmentRepositoryUri: 'https://some.uri',
                testRepositoryUri: 'https://some.uri',
                solutionRepositoryUri: 'https://some.uri',
                auxiliaryRepositoryUris: [],
                auxiliaryRepositoryCheckoutDirectories: [],
            },
            jobTimingInfo: {
                submissionDate: dayjs('2023-01-02'),
                buildStartDate: null,
                buildCompletionDate: null,
            },
            buildConfig: {
                dockerImage: 'someImage',
                commitHashToBuild: 'abc123',
                branch: 'main',
                programmingLanguage: 'Java',
                projectType: 'Maven',
                scaEnabled: false,
                sequentialTestRunsEnabled: false,
                testwiseCoverageEnabled: false,
                resultPaths: [],
            },
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
            repositoryInfo: {
                repositoryName: 'repo3',
                repositoryType: 'USER',
                triggeredByPushTo: 'USER',
                assignmentRepositoryUri: 'https://some.uri',
                testRepositoryUri: 'https://some.uri',
                solutionRepositoryUri: 'https://some.uri',
                auxiliaryRepositoryUris: [],
                auxiliaryRepositoryCheckoutDirectories: [],
            },
            jobTimingInfo: {
                submissionDate: dayjs('2023-01-03'),
                buildStartDate: null,
                buildCompletionDate: null,
            },
            buildConfig: {
                dockerImage: 'someImage',
                commitHashToBuild: 'abc125',
                branch: 'main',
                programmingLanguage: 'Java',
                projectType: 'Maven',
                scaEnabled: false,
                sequentialTestRunsEnabled: false,
                testwiseCoverageEnabled: false,
                resultPaths: [],
            },
        },
    ];
    const mockRunningJobs = [
        {
            id: '2',
            name: 'Build Job 2',
            buildAgentAddress: 'agent2',
            participationId: 102,
            courseId: 10,
            exerciseId: 100,
            retryCount: 0,
            priority: 3,
            repositoryInfo: {
                repositoryName: 'repo2',
                repositoryType: 'USER',
                triggeredByPushTo: 'USER',
                assignmentRepositoryUri: 'https://some.uri',
                testRepositoryUri: 'https://some.uri',
                solutionRepositoryUri: 'https://some.uri',
                auxiliaryRepositoryUris: [],
                auxiliaryRepositoryCheckoutDirectories: [],
            },
            jobTimingInfo: {
                submissionDate: dayjs('2023-01-01'),
                buildStartDate: dayjs('2023-01-01'),
                buildCompletionDate: null,
            },
            buildConfig: {
                dockerImage: 'someImage',
                commitHashToBuild: 'abc124',
                branch: 'main',
                programmingLanguage: 'Java',
                projectType: 'Maven',
                scaEnabled: false,
                sequentialTestRunsEnabled: false,
                testwiseCoverageEnabled: false,
                resultPaths: [],
            },
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
            repositoryInfo: {
                repositoryName: 'repo4',
                repositoryType: 'USER',
                triggeredByPushTo: 'USER',
                assignmentRepositoryUri: 'https://some.uri',
                testRepositoryUri: 'https://some.uri',
                solutionRepositoryUri: 'https://some.uri',
                auxiliaryRepositoryUris: [],
                auxiliaryRepositoryCheckoutDirectories: [],
            },
            jobTimingInfo: {
                submissionDate: dayjs('2023-01-04'),
                buildStartDate: dayjs('2023-01-04'),
                buildCompletionDate: null,
            },
            buildConfig: {
                dockerImage: 'someImage',
                commitHashToBuild: 'abc126',
                branch: 'main',
                programmingLanguage: 'Java',
                projectType: 'Maven',
                scaEnabled: false,
                sequentialTestRunsEnabled: false,
                testwiseCoverageEnabled: false,
                resultPaths: [],
            },
        },
    ];
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

    const mockBuildJobStatistics: BuildJobStatistics = {
        totalBuilds: 10,
        successfulBuilds: 5,
        failedBuilds: 3,
        cancelledBuilds: 2,
    };

    const mockFinishedJobsResponse: HttpResponse<FinishedBuildJob[]> = new HttpResponse({ body: mockFinishedJobs });

    const request = {
        page: 1,
        pageSize: 50,
        sortedColumn: 'buildCompletionDate',
        sortingOrder: SortingOrder.DESCENDING,
        searchTerm: '',
    };

    const filterOptionsEmpty = {
        buildAgentAddress: undefined,
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

    beforeEach(waitForAsync(() => {
        mockActivatedRoute = { params: of({ courseId: testCourseId }) };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, ArtemisSharedComponentModule, PieChartModule],
            declarations: [BuildQueueComponent, MockPipe(ArtemisTranslatePipe), MockComponent(DataTableComponent)],
            providers: [
                { provide: BuildQueueService, useValue: mockBuildQueueService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: DataTableComponent, useClass: DataTableComponent },
                { provide: LocalStorageService, useValue: mockLocalStorageService },
            ],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(BuildQueueComponent);
        component = fixture.componentInstance;
    });

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeDefined();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize all build job data', () => {
        // Mock ActivatedRoute to return an empty paramMap or a paramMap without 'courseId'
        mockActivatedRoute.paramMap = of(new Map([]));

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getQueuedBuildJobs.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs));
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        // Initialize the component
        component.ngOnInit();

        // Expectations: The service methods for general build jobs are called
        expect(mockBuildQueueService.getQueuedBuildJobs).toHaveBeenCalled();
        expect(mockBuildQueueService.getRunningBuildJobs).toHaveBeenCalled();
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalled();

        // Expectations: The service methods for course-specific build jobs are not called
        expect(mockBuildQueueService.getQueuedBuildJobsByCourseId).not.toHaveBeenCalled();
        expect(mockBuildQueueService.getRunningBuildJobsByCourseId).not.toHaveBeenCalled();
        expect(mockBuildQueueService.getFinishedBuildJobsByCourseId).not.toHaveBeenCalled();

        // Expectations: The component's properties are set with the mock data
        expect(component.queuedBuildJobs).toEqual(mockQueuedJobs);
        expect(component.runningBuildJobs).toEqual(mockRunningJobs);
        expect(component.finishedBuildJobs).toEqual(mockFinishedJobs);
    });

    it('should initialize with course data', () => {
        // Mock ActivatedRoute to return a specific course ID
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId.toString()]]));

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getQueuedBuildJobsByCourseId.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobsByCourseId.mockReturnValue(of(mockRunningJobs));
        mockBuildQueueService.getFinishedBuildJobsByCourseId.mockReturnValue(of(mockFinishedJobsResponse));
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(of(mockBuildJobStatistics));

        // Initialize the component
        component.ngOnInit();

        // Expectations: The service methods are called with the test course ID
        expect(mockBuildQueueService.getQueuedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId);
        expect(mockBuildQueueService.getRunningBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId);
        expect(mockBuildQueueService.getFinishedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId, request, filterOptionsEmpty);
        expect(mockBuildQueueService.getBuildJobStatisticsForCourse).toHaveBeenCalledWith(testCourseId, SpanType.WEEK);

        // Expectations: The component's properties are set with the mock data
        expect(component.queuedBuildJobs).toEqual(mockQueuedJobs);
        expect(component.runningBuildJobs).toEqual(mockRunningJobs);
        expect(component.finishedBuildJobs).toEqual(mockFinishedJobs);
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });

    it('should get build job statistics when changing the span', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([]));

        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.DAY);

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledTimes(2);
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });

    it('should not get build job statistics when span is the same', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([]));

        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of(mockBuildJobStatistics));

        component.ngOnInit();
        component.onTabChange(SpanType.WEEK);

        expect(mockBuildQueueService.getBuildJobStatistics).toHaveBeenCalledOnce();
        expect(component.buildJobStatistics).toEqual(mockBuildJobStatistics);
    });

    it('should refresh data', () => {
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId.toString()]]));
        const spy = jest.spyOn(component, 'ngOnInit');
        component.ngOnInit();
        expect(spy).toHaveBeenCalled();
    });

    it('should update build job duration in running build jobs', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([]));

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs));

        // Initialize the component and update the build job duration
        component.ngOnInit();
        component.runningBuildJobs = component.updateBuildJobDuration(component.runningBuildJobs); // This method is called in ngOnInit in interval callback, but we call it to add coverage

        // Expectations: The build job duration is calculated and set for each running build job
        for (const runningBuildJob of component.runningBuildJobs) {
            const { buildDuration, buildCompletionDate, buildStartDate } = runningBuildJob.jobTimingInfo!;
            if (buildDuration && buildCompletionDate && buildStartDate) {
                expect(buildDuration).toBeLessThanOrEqual(buildCompletionDate.diff(buildStartDate, 'seconds'));
            }
        }
    });

    it('should cancel a build job in a course', () => {
        const buildJobId = '1';

        // Mock ActivatedRoute to return a specific course ID
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId]]));

        // Mock BuildQueueService to return a successful response for canceling a build job
        mockBuildQueueService.cancelBuildJobInCourse.mockReturnValue(of(null));

        // Initialize the component
        component.ngOnInit();

        // Call the cancelBuildJob method
        component.cancelBuildJob(buildJobId);

        // Expectations: The service method for canceling a build job in a course is called with the correct parameters
        expect(mockBuildQueueService.cancelBuildJobInCourse).toHaveBeenCalledWith(testCourseId, buildJobId);
    });

    it('should cancel a build job without a course ID', () => {
        const buildJobId = '1';

        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([]));

        // Mock BuildQueueService to return a successful response for canceling a build job
        mockBuildQueueService.cancelBuildJob.mockReturnValue(of(null));

        // Initialize the component
        component.ngOnInit();

        // Call the cancelBuildJob method
        component.cancelBuildJob(buildJobId);

        // Expectations: The service method for canceling a build job is called without a course ID
        expect(mockBuildQueueService.cancelBuildJob).toHaveBeenCalledWith(buildJobId);
    });

    it('should cancel all queued build jobs in a course', () => {
        // Mock ActivatedRoute to return a specific course ID
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId.toString()]]));

        // Mock BuildQueueService to return a successful response for canceling all queued build jobs in a course
        mockBuildQueueService.cancelAllQueuedBuildJobsInCourse.mockReturnValue(of(null));

        // Initialize the component
        component.ngOnInit();

        // Call the cancelAllQueuedBuildJobsInCourse method
        component.cancelAllQueuedBuildJobs();

        // Expectations: The service method for canceling all queued build jobs in a course is called with the correct parameter
        expect(mockBuildQueueService.cancelAllQueuedBuildJobsInCourse).toHaveBeenCalledWith(testCourseId);
    });

    it('should cancel all running build jobs in a course', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId.toString()]]));

        // Mock BuildQueueService to return a successful response for canceling all running build jobs
        mockBuildQueueService.cancelAllRunningBuildJobsInCourse.mockReturnValue(of(null));

        // Initialize the component
        component.ngOnInit();

        // Call the cancelAllRunningBuildJobs method
        component.cancelAllRunningBuildJobs();

        // Expectations: The service method for canceling all running build jobs is called with the correct parameter
        expect(mockBuildQueueService.cancelAllRunningBuildJobsInCourse).toHaveBeenCalledWith(testCourseId);
    });

    it('should cancel all queued build jobs', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([]));

        // Mock BuildQueueService to return a successful response for canceling all running build jobs
        mockBuildQueueService.cancelAllQueuedBuildJobs.mockReturnValue(of(null));

        // Initialize the component
        component.ngOnInit();

        // Call the cancelAllRunningBuildJobs method
        component.cancelAllQueuedBuildJobs();

        // Expectations: The service method for canceling all running build jobs is called without a course ID
        expect(mockBuildQueueService.cancelAllQueuedBuildJobs).toHaveBeenCalled();
    });

    it('should cancel all running build jobs', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([]));

        // Mock BuildQueueService to return a successful response for canceling all running build jobs
        mockBuildQueueService.cancelAllRunningBuildJobs.mockReturnValue(of(null));

        // Initialize the component
        component.ngOnInit();

        // Call the cancelAllRunningBuildJobs method
        component.cancelAllRunningBuildJobs();

        // Expectations: The service method for canceling all running build jobs is called without a course ID
        expect(mockBuildQueueService.cancelAllRunningBuildJobs).toHaveBeenCalled();
    });

    it('should load finished build jobs on initialization', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([]));

        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

        component.ngOnInit();

        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalledWith(request, filterOptionsEmpty);
        expect(component.finishedBuildJobs).toEqual(mockFinishedJobs);
    });

    it('should load finished build jobs for a specific course on initialization', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId.toString()]]));

        mockBuildQueueService.getFinishedBuildJobsByCourseId.mockReturnValue(of(mockFinishedJobsResponse));

        component.ngOnInit();

        expect(mockBuildQueueService.getFinishedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId, request, filterOptionsEmpty);
        expect(component.finishedBuildJobs).toEqual(mockFinishedJobs);
    });

    it('should return correct build agent addresses', () => {
        component.finishedBuildJobs = mockFinishedJobs;
        expect(component.buildAgentAddresses).toEqual(['agent5', 'agent6']);
    });

    it('should trigger refresh on search term change', async () => {
        mockActivatedRoute.paramMap = of(new Map([]));

        mockBuildQueueService.getQueuedBuildJobs.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs));
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

        component.ngOnInit();
        component.searchTerm = 'search';
        component.triggerLoadFinishedJobs();

        const requestWithSearchTerm = { ...request };
        requestWithSearchTerm.searchTerm = 'search';
        // Wait for the debounce time to pass
        await new Promise((resolve) => setTimeout(resolve, 110));
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenNthCalledWith(2, requestWithSearchTerm, filterOptionsEmpty);
    });

    it('should set build job duration', () => {
        // Mock ActivatedRoute to return no course ID
        mockActivatedRoute.paramMap = of(new Map([]));

        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

        component.ngOnInit();

        for (const finishedBuildJob of component.finishedBuildJobs) {
            const { buildDuration, buildCompletionDate, buildStartDate } = finishedBuildJob;
            if (buildDuration && buildCompletionDate && buildStartDate) {
                expect(buildDuration).toEqual((buildCompletionDate.diff(buildStartDate, 'milliseconds') / 1000).toFixed(3) + 's');
            }
        }
    });

    it('should return correct number of filters applied', () => {
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();
        component.finishedBuildJobFilter.buildAgentAddress = 'agent1';
        component.filterBuildAgentAddressChanged();
        component.finishedBuildJobFilter.buildDurationFilterLowerBound = 1;
        component.finishedBuildJobFilter.buildDurationFilterUpperBound = 2;
        component.filterDurationChanged();
        component.finishedBuildJobFilter.buildStartDateFilterFrom = dayjs('2023-01-01');
        component.finishedBuildJobFilter.buildStartDateFilterTo = dayjs('2023-01-02');
        component.filterDateChanged();
        component.toggleBuildStatusFilter('SUCCESSFUL');

        expect(component.finishedBuildJobFilter.numberOfAppliedFilters).toBe(6);

        component.finishedBuildJobFilter.buildAgentAddress = undefined;
        component.filterBuildAgentAddressChanged();
        component.finishedBuildJobFilter.buildDurationFilterLowerBound = undefined;
        component.filterDurationChanged();

        expect(component.finishedBuildJobFilter.numberOfAppliedFilters).toBe(4);
    });

    it('should save filter in local storage', () => {
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();
        component.finishedBuildJobFilter.buildAgentAddress = 'agent1';
        component.finishedBuildJobFilter.buildDurationFilterLowerBound = 1;
        component.finishedBuildJobFilter.buildDurationFilterUpperBound = 2;
        component.finishedBuildJobFilter.buildStartDateFilterFrom = dayjs('2023-01-01');
        component.finishedBuildJobFilter.buildStartDateFilterTo = dayjs('2023-01-02');
        component.finishedBuildJobFilter.status = 'SUCCESSFUL';

        component.filterDurationChanged();
        component.filterDateChanged();
        component.filterBuildAgentAddressChanged();
        component.toggleBuildStatusFilter('SUCCESSFUL');

        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildAgentAddress)).toBe('agent1');
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildDurationFilterLowerBound)).toBe(1);
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildDurationFilterUpperBound)).toBe(2);
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildStartDateFilterFrom)).toEqual(dayjs('2023-01-01'));
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildStartDateFilterTo)).toEqual(dayjs('2023-01-02'));
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.status)).toBe('SUCCESSFUL');

        component.finishedBuildJobFilter = new FinishedBuildJobFilter();

        component.filterDurationChanged();
        component.filterDateChanged();
        component.filterBuildAgentAddressChanged();
        component.toggleBuildStatusFilter();

        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildAgentAddress)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildDurationFilterLowerBound)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildDurationFilterUpperBound)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildStartDateFilterFrom)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.buildStartDateFilterTo)).toBeUndefined();
        expect(mockLocalStorageService.retrieve(FinishedBuildJobFilterStorageKey.status)).toBeUndefined();
    });

    it('should validate correctly', () => {
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();
        component.finishedBuildJobFilter.buildDurationFilterLowerBound = 1;
        component.finishedBuildJobFilter.buildStartDateFilterFrom = dayjs('2023-01-01');
        component.filterDurationChanged();
        component.filterDateChanged();

        expect(component.finishedBuildJobFilter.areDatesValid).toBeTruthy();
        expect(component.finishedBuildJobFilter.areDurationFiltersValid).toBeTruthy();

        component.finishedBuildJobFilter.buildDurationFilterUpperBound = 2;
        component.finishedBuildJobFilter.buildStartDateFilterTo = dayjs('2023-01-02');
        component.filterDurationChanged();
        component.filterDateChanged();

        expect(component.finishedBuildJobFilter.areDatesValid).toBeTruthy();
        expect(component.finishedBuildJobFilter.areDurationFiltersValid).toBeTruthy();

        component.finishedBuildJobFilter.buildDurationFilterLowerBound = 3;
        component.finishedBuildJobFilter.buildStartDateFilterFrom = dayjs('2023-01-03');
        component.filterDurationChanged();
        component.filterDateChanged();

        expect(component.finishedBuildJobFilter.areDatesValid).toBeFalsy();
        expect(component.finishedBuildJobFilter.areDurationFiltersValid).toBeFalsy();
    });
});

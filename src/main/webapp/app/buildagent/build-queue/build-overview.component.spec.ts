import { ComponentFixture, ComponentFixtureAutoDetect, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { BuildOverviewComponent } from 'app/buildagent/build-queue/build-overview.component';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { HttpResponse } from '@angular/common/http';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';
import * as DownloadUtil from '../../shared/util/download.util';
import { FinishedBuildJobFilter } from 'app/buildagent/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

class ActivatedRouteStub {
    private params$ = new BehaviorSubject<{ [key: string]: any }>({});
    private paramMap$ = new BehaviorSubject(convertToParamMap({}));
    params = this.params$.asObservable();
    paramMap = this.paramMap$.asObservable();
    snapshot = { paramMap: convertToParamMap({}) };
    url = of([{ path: 'build-queue' }]);

    setParamMap(params: Record<string, any>) {
        this.params$.next(params);
        const paramMapValue = convertToParamMap(params);
        this.paramMap$.next(paramMapValue);
        this.snapshot = { paramMap: paramMapValue };
    }
}

describe('BuildQueueComponent', () => {
    setupTestBed({ zoneless: true });

    let component: BuildOverviewComponent;
    let fixture: ComponentFixture<BuildOverviewComponent>;

    const mockBuildQueueService = {
        getQueuedBuildJobsByCourseId: vi.fn(),
        getRunningBuildJobsByCourseId: vi.fn(),
        getQueuedBuildJobs: vi.fn(),
        getRunningBuildJobs: vi.fn(),
        cancelBuildJobInCourse: vi.fn(),
        cancelBuildJob: vi.fn(),
        cancelAllQueuedBuildJobsInCourse: vi.fn(),
        cancelAllRunningBuildJobsInCourse: vi.fn(),
        cancelAllQueuedBuildJobs: vi.fn(),
        cancelAllRunningBuildJobs: vi.fn(),
        getFinishedBuildJobsByCourseId: vi.fn(),
        getFinishedBuildJobs: vi.fn(),
        getBuildJobStatistics: vi.fn(),
        getBuildJobStatisticsForCourse: vi.fn(),
        getBuildJobLogs: vi.fn(),
    };

    const accountServiceMock = { identity: vi.fn(), getAuthenticationState: vi.fn() };

    const testCourseId = 123;

    const routeStub = new ActivatedRouteStub();
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
            buildSubmissionDate: dayjs('2023-01-05'),
            buildStartDate: dayjs('2023-01-05'),
            buildCompletionDate: dayjs('2023-01-05'),
            buildDuration: '0.0s',
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
            buildDuration: '0.0s',
            commitHash: 'abc128',
        },
    ];

    const mockFinishedJobsResponse: HttpResponse<FinishedBuildJob[]> = new HttpResponse({ body: mockFinishedJobs });

    const request = {
        page: 1,
        pageSize: 50,
        sortedColumn: 'buildSubmissionDate',
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

    let modalService: NgbModal;

    beforeEach(async () => {
        // Set default return values for all methods
        mockBuildQueueService.getQueuedBuildJobs.mockReturnValue(of([]));
        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of([]));
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(new HttpResponse({ body: [] })));
        mockBuildQueueService.getQueuedBuildJobsByCourseId.mockReturnValue(of([]));
        mockBuildQueueService.getRunningBuildJobsByCourseId.mockReturnValue(of([]));
        mockBuildQueueService.getFinishedBuildJobsByCourseId.mockReturnValue(of(new HttpResponse({ body: [] })));
        mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of({}));
        mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(of({}));

        TestBed.configureTestingModule({
            imports: [BuildOverviewComponent],
            providers: [
                { provide: ComponentFixtureAutoDetect, useValue: false },
                { provide: BuildOverviewService, useValue: mockBuildQueueService },
                { provide: ActivatedRoute, useValue: routeStub },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: DataTableComponent, useClass: DataTableComponent },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
            schemas: [NO_ERRORS_SCHEMA],
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(BuildOverviewComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeDefined();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize all build job data', () => {
        // Mock ActivatedRoute to return an empty paramMap or a paramMap without 'courseId'
        routeStub.setParamMap({});

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getQueuedBuildJobs.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs));
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

        // Initialize the component
        component.ngOnInit();

        // Admin view should be enabled when no courseId is present
        expect(component.isAdministrationView()).toBe(true);

        // Expectations: The service methods for general build jobs are called
        expect(mockBuildQueueService.getQueuedBuildJobs).toHaveBeenCalled();
        expect(mockBuildQueueService.getRunningBuildJobs).toHaveBeenCalled();
        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalled();

        // Expectations: The service methods for course-specific build jobs are not called
        expect(mockBuildQueueService.getQueuedBuildJobsByCourseId).not.toHaveBeenCalled();
        expect(mockBuildQueueService.getRunningBuildJobsByCourseId).not.toHaveBeenCalled();
        expect(mockBuildQueueService.getFinishedBuildJobsByCourseId).not.toHaveBeenCalled();

        // Expectations: The component's properties are set with the mock data
        expect(component.queuedBuildJobs()).toEqual(mockQueuedJobs);
        expect(component.runningBuildJobs()).toEqual(mockRunningJobs);
        expect(component.finishedBuildJobs()).toEqual(mockFinishedJobs);
    });

    it('should initialize with course data', () => {
        // Mock ActivatedRoute to return a specific course ID
        routeStub.setParamMap({ courseId: testCourseId.toString() });

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getQueuedBuildJobsByCourseId.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobsByCourseId.mockReturnValue(of(mockRunningJobs));
        mockBuildQueueService.getFinishedBuildJobsByCourseId.mockReturnValue(of(mockFinishedJobsResponse));

        // Initialize the component
        component.ngOnInit();

        // Admin view should be disabled when courseId is present
        expect(component.isAdministrationView()).toBe(false);

        // Expectations: The service methods are called with the test course ID
        expect(mockBuildQueueService.getQueuedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId);
        expect(mockBuildQueueService.getRunningBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId);
        expect(mockBuildQueueService.getFinishedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId, request, filterOptionsEmpty);

        // Expectations: The service methods for general build jobs should not be called
        expect(mockBuildQueueService.getQueuedBuildJobs).not.toHaveBeenCalled();
        expect(mockBuildQueueService.getRunningBuildJobs).not.toHaveBeenCalled();
        expect(mockBuildQueueService.getFinishedBuildJobs).not.toHaveBeenCalled();

        // Expectations: The component's properties are set with the mock data
        expect(component.queuedBuildJobs()).toEqual(mockQueuedJobs);
        expect(component.runningBuildJobs()).toEqual(mockRunningJobs);
        expect(component.finishedBuildJobs()).toEqual(mockFinishedJobs);
    });

    it('should refresh data', () => {
        routeStub.setParamMap({ courseId: testCourseId.toString() });
        const spy = vi.spyOn(component, 'ngOnInit');
        component.ngOnInit();
        expect(spy).toHaveBeenCalled();
    });

    it('should update build job duration in running build jobs', () => {
        // Mock ActivatedRoute to return no course ID
        routeStub.setParamMap({});

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs));

        // Initialize the component and update the build job duration
        component.ngOnInit();
        component.runningBuildJobs.set(component.updateBuildJobDuration(component.runningBuildJobs())); // This method is called in ngOnInit in interval callback, but we call it to add coverage

        // Expectations: The build job duration is calculated and set for each running build job
        for (const runningBuildJob of component.runningBuildJobs()) {
            const { buildDuration, buildCompletionDate, buildStartDate } = runningBuildJob.jobTimingInfo!;
            if (buildDuration && buildCompletionDate && buildStartDate) {
                expect(buildDuration).toBeLessThanOrEqual(buildCompletionDate.diff(buildStartDate, 'seconds'));
            }
        }
    });

    it('should cancel a build job in a course', () => {
        const buildJobId = '1';

        // Mock ActivatedRoute to return a specific course ID
        routeStub.setParamMap({ courseId: testCourseId.toString() });

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
        routeStub.setParamMap({});

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
        routeStub.setParamMap({ courseId: testCourseId.toString() });

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
        routeStub.setParamMap({ courseId: testCourseId.toString() });

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
        routeStub.setParamMap({});

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
        routeStub.setParamMap({});

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
        routeStub.setParamMap({});

        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

        component.ngOnInit();

        expect(mockBuildQueueService.getFinishedBuildJobs).toHaveBeenCalledWith(request, filterOptionsEmpty);
        expect(component.finishedBuildJobs()).toEqual(mockFinishedJobs);
    });

    it('should load finished build jobs for a specific course on initialization', () => {
        // Mock ActivatedRoute to return no course ID
        routeStub.setParamMap({ courseId: testCourseId.toString() });

        mockBuildQueueService.getFinishedBuildJobsByCourseId.mockReturnValue(of(mockFinishedJobsResponse));

        component.ngOnInit();

        expect(mockBuildQueueService.getFinishedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId, request, filterOptionsEmpty);
        expect(component.finishedBuildJobs()).toEqual(mockFinishedJobs);
    });

    it('should trigger refresh on search term change', async () => {
        routeStub.setParamMap({});

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
        routeStub.setParamMap({});

        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

        component.ngOnInit();

        for (const finishedBuildJob of component.finishedBuildJobs()) {
            const { buildDuration, buildCompletionDate, buildStartDate } = finishedBuildJob;
            if (buildDuration && buildCompletionDate && buildStartDate) {
                // Component formats with 1 decimal place for durations under 60s
                const durationSeconds = buildCompletionDate.diff(buildStartDate, 'milliseconds') / 1000;
                const expectedDuration = durationSeconds.toFixed(1) + 's';
                expect(buildDuration).toEqual(expectedDuration);
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
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();

        component.openFilterModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(modalRef.componentInstance.finishedBuildJobFilter).toEqual(filterOptionsEmpty);
        expect(modalRef.componentInstance.finishedBuildJobs).toEqual(component.finishedBuildJobs());
        expect(modalRef.componentInstance.buildAgentFilterable).toBeTruthy();
    });

    describe('BuildOverviewComponent Download Logs', () => {
        let alertService: AlertService;
        let originalClick: typeof HTMLAnchorElement.prototype.click;
        let originalURL: typeof window.URL;

        beforeEach(() => {
            alertService = TestBed.inject(AlertService);

            originalClick = HTMLAnchorElement.prototype.click;
            HTMLAnchorElement.prototype.click = vi.fn();

            originalURL = window.URL;
        });

        afterEach(() => {
            HTMLAnchorElement.prototype.click = originalClick;

            // restore URL
            Object.defineProperty(window, 'URL', {
                value: originalURL,
                writable: true,
                configurable: true,
            });

            vi.restoreAllMocks();
        });

        it('should show error alert when browser API is missing', async () => {
            const buildJobId = '1';
            const logs = 'log1\nlog2\nlog3';

            mockBuildQueueService.getBuildJobLogs.mockReturnValue(of(logs));
            const downloadSpy = vi.spyOn(DownloadUtil, 'downloadFile');
            const alertSpy = vi.spyOn(alertService, 'error');

            Object.defineProperty(window, 'URL', {
                value: {},
                writable: true,
                configurable: true,
            });

            component.viewBuildLogs(undefined, buildJobId);
            component.downloadBuildLogs();

            expect(downloadSpy).toHaveBeenCalledWith(expect.any(Blob), `${buildJobId}.log`);

            const [blobArg, filenameArg] = downloadSpy.mock.calls[0] as [Blob, string];
            expect(filenameArg).toBe(`${buildJobId}.log`);
            expect(blobArg.type).toBe('text/plain');
            expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();
            expect(alertSpy).toHaveBeenCalled();
        });

        it('should download file when browser API is available', async () => {
            const buildJobId = '1';
            const logs = 'log1\nlog2\nlog3';

            mockBuildQueueService.getBuildJobLogs.mockReturnValue(of(logs));
            const downloadSpy = vi.spyOn(DownloadUtil, 'downloadFile');
            const alertSpy = vi.spyOn(alertService, 'error');

            Object.defineProperty(window, 'URL', {
                value: {
                    createObjectURL: vi.fn(() => 'mock-url'),
                    revokeObjectURL: vi.fn(),
                },
                writable: true,
                configurable: true,
            });

            component.viewBuildLogs(undefined, buildJobId);
            component.downloadBuildLogs();

            expect(downloadSpy).toHaveBeenCalledWith(expect.any(Blob), `${buildJobId}.log`);

            const [blobArg, filenameArg] = downloadSpy.mock.calls[0] as [Blob, string];
            expect(filenameArg).toBe(`${buildJobId}.log`);
            expect(blobArg.type).toBe('text/plain');
            expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
            expect(alertSpy).not.toHaveBeenCalled();
        });
    });

    it('should navigate to job detail page in admin view', () => {
        routeStub.setParamMap({});
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.ngOnInit();
        component.navigateToJobDetail('test-job-123');

        expect(navigateSpy).toHaveBeenCalledWith(['/admin', 'build-overview', 'test-job-123', 'job-details']);
    });

    it('should navigate to job detail page in course view', () => {
        routeStub.setParamMap({ courseId: testCourseId.toString() });
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.ngOnInit();
        component.navigateToJobDetail('test-job-123');

        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', testCourseId, 'build-overview', 'test-job-123', 'job-details']);
    });

    it('should not navigate if jobId is undefined', () => {
        routeStub.setParamMap({});
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.ngOnInit();
        component.navigateToJobDetail(undefined);

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should format finished duration correctly for under 60 seconds', () => {
        expect(component.formatFinishedDuration(45.333)).toBe('45.3s');
        expect(component.formatFinishedDuration(0)).toBe('0.0s');
        expect(component.formatFinishedDuration(59.99)).toBe('60.0s');
    });

    it('should format finished duration correctly for 60+ seconds', () => {
        expect(component.formatFinishedDuration(60)).toBe('1m 0s');
        expect(component.formatFinishedDuration(90)).toBe('1m 30s');
        expect(component.formatFinishedDuration(125)).toBe('2m 5s');
    });

    it('should calculate waiting time correctly for seconds', () => {
        const submissionDate = dayjs().subtract(30, 'seconds');
        const result = component.calculateWaitingTime(submissionDate);
        expect(result).toMatch(/^\d+s$/);
    });

    it('should calculate waiting time correctly for minutes', () => {
        const submissionDate = dayjs().subtract(5, 'minutes');
        const result = component.calculateWaitingTime(submissionDate);
        expect(result).toMatch(/^\d+m \d+s$/);
    });

    it('should calculate waiting time correctly for hours', () => {
        const submissionDate = dayjs().subtract(2, 'hours');
        const result = component.calculateWaitingTime(submissionDate);
        expect(result).toMatch(/^\d+h \d+m$/);
    });

    it('should return dash for undefined submission date', () => {
        const result = component.calculateWaitingTime(undefined);
        expect(result).toBe('-');
    });
});

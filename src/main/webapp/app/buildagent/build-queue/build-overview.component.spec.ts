import { ComponentFixture, ComponentFixtureAutoDetect, TestBed, waitForAsync } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { BuildOverviewComponent } from 'app/buildagent/build-queue/build-overview.component';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { BuildJobStatistics, FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
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
import { Component, Input } from '@angular/core';
import { By } from '@angular/platform-browser';
import { BuildJobStatisticsComponent } from 'app/buildagent/build-job-statistics/build-job-statistics.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
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

@Component({
    selector: 'jhi-build-job-statistics',
    template: '<div></div>',
    standalone: true,
})
class StubBuildJobStatisticsComponent {
    @Input() courseId?: number;
    @Input() buildJobStatisticsInput?: BuildJobStatistics;
}

describe('BuildQueueComponent', () => {
    let component: BuildOverviewComponent;
    let fixture: ComponentFixture<BuildOverviewComponent>;

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
        getBuildJobLogs: jest.fn(),
    };

    const accountServiceMock = { identity: jest.fn(), getAuthenticationState: jest.fn() };

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
            buildAgent: { name: 'agent1' },
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
            buildAgent: { name: 'agent3' },
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
            buildAgent: { name: 'agent2' },
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
            buildAgent: { name: 'agent4' },
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

    let alertService: AlertService;
    let alertServiceErrorStub: jest.SpyInstance;
    let modalService: NgbModal;

    beforeEach(waitForAsync(() => {
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
        }).compileComponents();

        fixture = TestBed.createComponent(BuildOverviewComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        modalService = TestBed.inject(NgbModal);
        alertServiceErrorStub = jest.spyOn(alertService, 'error');
    }));

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
        routeStub.setParamMap({});

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getQueuedBuildJobs.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs));
        mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

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
        routeStub.setParamMap({ courseId: testCourseId.toString() });

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getQueuedBuildJobsByCourseId.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobsByCourseId.mockReturnValue(of(mockRunningJobs));
        mockBuildQueueService.getFinishedBuildJobsByCourseId.mockReturnValue(of(mockFinishedJobsResponse));

        // Initialize the component
        component.ngOnInit();

        // Expectations: The service methods are called with the test course ID
        expect(mockBuildQueueService.getQueuedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId);
        expect(mockBuildQueueService.getRunningBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId);
        expect(mockBuildQueueService.getFinishedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId, request, filterOptionsEmpty);

        // Expectations: The component's properties are set with the mock data
        expect(component.queuedBuildJobs).toEqual(mockQueuedJobs);
        expect(component.runningBuildJobs).toEqual(mockRunningJobs);
        expect(component.finishedBuildJobs).toEqual(mockFinishedJobs);
    });

    it('should refresh data', () => {
        routeStub.setParamMap({ courseId: testCourseId.toString() });
        const spy = jest.spyOn(component, 'ngOnInit');
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
        expect(component.finishedBuildJobs).toEqual(mockFinishedJobs);
    });

    it('should load finished build jobs for a specific course on initialization', () => {
        // Mock ActivatedRoute to return no course ID
        routeStub.setParamMap({ courseId: testCourseId.toString() });

        mockBuildQueueService.getFinishedBuildJobsByCourseId.mockReturnValue(of(mockFinishedJobsResponse));

        component.ngOnInit();

        expect(mockBuildQueueService.getFinishedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId, request, filterOptionsEmpty);
        expect(component.finishedBuildJobs).toEqual(mockFinishedJobs);
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
        component.finishedBuildJobFilter = new FinishedBuildJobFilter();

        component.openFilterModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(modalRef.componentInstance.finishedBuildJobFilter).toEqual(filterOptionsEmpty);
        expect(modalRef.componentInstance.finishedBuildJobs).toEqual(component.finishedBuildJobs);
        expect(modalRef.componentInstance.buildAgentFilterable).toBeTrue();
    });

    describe('Course ID column visibility', () => {
        beforeEach(waitForAsync(() => {
            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                imports: [BuildOverviewComponent],
                providers: [
                    { provide: BuildOverviewService, useValue: mockBuildQueueService },
                    { provide: ActivatedRoute, useValue: routeStub },
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: NgbModal, useClass: MockNgbModalService },
                    { provide: WebsocketService, useClass: MockWebsocketService },
                    { provide: FeatureToggleService, useValue: { isEnabled: () => false } },
                    provideHttpClientTesting(),
                ],
            })
                .overrideComponent(BuildOverviewComponent, {
                    remove: {
                        imports: [BuildJobStatisticsComponent],
                    },
                    add: {
                        imports: [StubBuildJobStatisticsComponent],
                    },
                })
                .compileComponents();

            fixture = TestBed.createComponent(BuildOverviewComponent);
            component = fixture.componentInstance;
        }));

        it('should show Course ID column in administration view', () => {
            const getFinishedCourseIdLinks = () =>
                fixture.debugElement.queryAll(By.css('td.finish-jobs-column a[href^="/course-management/"]:not([href*="programming-exercises"])'));
            const getAnyCourseIdHeader = () => fixture.debugElement.queryAll(By.css('[jhitranslate="artemisApp.buildQueue.buildJob.courseId"]'));

            routeStub.setParamMap({});

            mockBuildQueueService.getQueuedBuildJobs.mockReturnValue(of(mockQueuedJobs));
            mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs));
            mockBuildQueueService.getFinishedBuildJobs.mockReturnValue(of(mockFinishedJobsResponse));

            mockBuildQueueService.getBuildJobStatistics.mockReturnValue(of({}));
            mockBuildQueueService.getBuildJobStatisticsForCourse.mockReturnValue(of({}));

            component.ngOnInit();
            fixture.detectChanges();

            expect(component.isAdministrationView()).toBeTrue();

            const finishedLinks = getFinishedCourseIdLinks();
            expect(finishedLinks).toHaveLength(mockFinishedJobs.length);

            const textValues = finishedLinks.map((de) => (de.nativeElement as HTMLAnchorElement).textContent?.trim());
            expect(textValues).toEqual(mockFinishedJobs.map((job) => `${job.courseId}`));

            const datatableCourseIdHeaders = getAnyCourseIdHeader();
            expect(datatableCourseIdHeaders).toHaveLength(3);
        });

        it('should hide Course ID column in course view', () => {
            const getFinishedCourseIdLinks = () =>
                fixture.debugElement.queryAll(By.css('td.finish-jobs-column a[href^="/course-management/"]:not([href*="programming-exercises"])'));
            const getAnyCourseIdHeader = () => fixture.debugElement.queryAll(By.css('[jhitranslate="artemisApp.buildQueue.buildJob.courseId"]'));
            routeStub.setParamMap({ courseId: '123' });

            mockBuildQueueService.getQueuedBuildJobsByCourseId.mockReturnValue(of(mockQueuedJobs));
            mockBuildQueueService.getRunningBuildJobsByCourseId.mockReturnValue(of(mockRunningJobs));
            mockBuildQueueService.getFinishedBuildJobsByCourseId.mockReturnValue(of(mockFinishedJobsResponse));

            component.ngOnInit();
            fixture.detectChanges();

            expect(component.isAdministrationView()).toBeFalse();

            const finishedLinks = getFinishedCourseIdLinks();
            expect(finishedLinks).toHaveLength(0);

            const datatableCourseIdHeaders = getAnyCourseIdHeader();
            expect(datatableCourseIdHeaders).toHaveLength(0);
        });
    });

    describe('BuildOverviewComponent Download Logs', () => {
        let originalClick: typeof HTMLAnchorElement.prototype.click;

        beforeEach(() => {
            originalClick = HTMLAnchorElement.prototype.click;
            HTMLAnchorElement.prototype.click = jest.fn();
        });

        afterEach(() => {
            HTMLAnchorElement.prototype.click = originalClick;
            jest.restoreAllMocks();
        });

        it('should show error alert when browser API is missing', () => {
            const buildJobId = '1';
            const logs = 'log1\nlog2\nlog3';
            const mockBlob = new Blob([logs], { type: 'text/plain' });
            mockBuildQueueService.getBuildJobLogs.mockReturnValue(of(logs));
            const downloadSpy = jest.spyOn(DownloadUtil, 'downloadFile');

            Object.defineProperty(window, 'URL', {
                value: {},
                writable: true,
            });

            component.viewBuildLogs(undefined, buildJobId);
            component.downloadBuildLogs();

            expect(downloadSpy).toHaveBeenCalledWith(mockBlob, `${buildJobId}.log`);

            expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();

            expect(alertServiceErrorStub).toHaveBeenCalled();
        });

        it('should download file when browser API is available', () => {
            const buildJobId = '1';
            const logs = 'log1\nlog2\nlog3';
            const mockBlob = new Blob([logs], { type: 'text/plain' });
            mockBuildQueueService.getBuildJobLogs.mockReturnValue(of(logs));
            const downloadSpy = jest.spyOn(DownloadUtil, 'downloadFile');

            Object.defineProperty(window, 'URL', {
                value: {
                    createObjectURL: jest.fn(() => 'mock-url'),
                    revokeObjectURL: jest.fn(),
                },
                writable: true,
                configurable: true,
            });

            component.viewBuildLogs(undefined, buildJobId);
            component.downloadBuildLogs();

            expect(downloadSpy).toHaveBeenCalledWith(mockBlob, `${buildJobId}.log`);

            expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();

            expect(alertServiceErrorStub).not.toHaveBeenCalled();
        });
    });
});

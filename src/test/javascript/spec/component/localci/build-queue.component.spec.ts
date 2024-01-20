import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { BuildQueueComponent } from 'app/localci/build-queue/build-queue.component';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisTestModule } from '../../test.module';

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
        cancelAllQueuedBuildJobsInCourse: jest.fn(),
        cancelAllRunningBuildJobsInCourse: jest.fn(),
        cancelAllQueuedBuildJobs: jest.fn(),
        cancelAllRunningBuildJobs: jest.fn(),
    };

    const accountServiceMock = { identity: jest.fn(), getAuthenticationState: jest.fn() };

    const testCourseId = 123;
    const mockQueuedJobs = [
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
    const mockRunningJobs = [
        {
            id: 2,
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
            id: 4,
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

    beforeEach(waitForAsync(() => {
        mockActivatedRoute = { params: of({ courseId: testCourseId }) };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule],
            declarations: [BuildQueueComponent, MockPipe(ArtemisTranslatePipe), MockComponent(DataTableComponent)],
            providers: [
                { provide: BuildQueueService, useValue: mockBuildQueueService },
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: DataTableComponent, useClass: DataTableComponent },
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

    it('should call getQueuedBuildJobs and getRunningBuildJobs when no courseId is provided', () => {
        // Mock ActivatedRoute to return an empty paramMap or a paramMap without 'courseId'
        mockActivatedRoute.paramMap = of(new Map([]));

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getQueuedBuildJobs.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobs.mockReturnValue(of(mockRunningJobs));

        // Initialize the component
        component.ngOnInit();

        // Expectations: The service methods for general build jobs are called
        expect(mockBuildQueueService.getQueuedBuildJobs).toHaveBeenCalled();
        expect(mockBuildQueueService.getRunningBuildJobs).toHaveBeenCalled();

        // Expectations: The service methods for course-specific build jobs are not called
        expect(mockBuildQueueService.getQueuedBuildJobsByCourseId).not.toHaveBeenCalled();
        expect(mockBuildQueueService.getRunningBuildJobsByCourseId).not.toHaveBeenCalled();

        // Expectations: The component's properties are set with the mock data
        expect(component.queuedBuildJobs).toEqual(mockQueuedJobs);
        expect(component.runningBuildJobs).toEqual(mockRunningJobs);
    });

    it('should initialize with course data', () => {
        // Mock ActivatedRoute to return a specific course ID
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId.toString()]]));

        // Mock BuildQueueService to return mock data
        mockBuildQueueService.getQueuedBuildJobsByCourseId.mockReturnValue(of(mockQueuedJobs));
        mockBuildQueueService.getRunningBuildJobsByCourseId.mockReturnValue(of(mockRunningJobs));

        // Initialize the component
        component.ngOnInit();

        // Expectations: The service methods are called with the test course ID
        expect(mockBuildQueueService.getQueuedBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId);
        expect(mockBuildQueueService.getRunningBuildJobsByCourseId).toHaveBeenCalledWith(testCourseId);

        // Expectations: The component's properties are set with the mock data
        expect(component.queuedBuildJobs).toEqual(mockQueuedJobs);
        expect(component.runningBuildJobs).toEqual(mockRunningJobs);
    });

    it('should refresh data', () => {
        mockActivatedRoute.paramMap = of(new Map([['courseId', testCourseId.toString()]]));
        const spy = jest.spyOn(component, 'ngOnInit');
        component.ngOnInit();
        expect(spy).toHaveBeenCalled();
    });

    it('should cancel a build job in a course', () => {
        const buildJobId = 1;

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
});

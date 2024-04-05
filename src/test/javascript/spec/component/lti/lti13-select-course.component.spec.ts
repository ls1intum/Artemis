import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { of } from 'rxjs';
import { LtiCoursesComponent } from 'app/lti/lti13-select-course.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { LtiCourseCardComponent } from 'app/lti/lti-course-card.component';
import { OnlineCourseDtoModel } from 'app/lti/online-course-dto.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';
import { AlertService } from 'app/core/util/alert.service';

describe('LtiCoursesComponent', () => {
    let component: LtiCoursesComponent;
    let fixture: ComponentFixture<LtiCoursesComponent>;
    let courseManagementService: CourseManagementService;
    let sessionStorageService: SessionStorageService;
    let sessionStorageRetrieveSpy: jest.SpyInstance;

    const mockCourses: OnlineCourseDtoModel[] = [
        { id: 1, title: 'Course A', shortName: 'cA', registrationId: '1' },
        { id: 2, title: 'Course B', shortName: 'cB', registrationId: '1' },
        { id: 3, title: 'Course C', shortName: 'cC', registrationId: '1' },
    ];

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [LtiCoursesComponent, MockComponent(LtiCourseCardComponent)],
            providers: [
                MockProvider(CourseManagementService, {
                    findAllOnlineCoursesWithRegistrationId: jest.fn().mockReturnValue(of(mockCourses)),
                }),
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                jest.fn().mockReturnValue(of(mockCourses));
                courseManagementService = TestBed.inject(CourseManagementService);
                sessionStorageService = TestBed.inject(SessionStorageService);
                sessionStorageRetrieveSpy = jest.spyOn(sessionStorageService, 'retrieve');
                fixture = TestBed.createComponent(LtiCoursesComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load and filter courses on ngOnInit', fakeAsync(() => {
        sessionStorageRetrieveSpy.mockReturnValue('1');

        component.ngOnInit();
        tick();

        fixture.whenStable().then(() => {
            expect(courseManagementService.findAllOnlineCoursesWithRegistrationId).toHaveBeenCalled();
            expect(component.courses).toHaveLength(3);
            expect(component.courses[0].title).toBe('Course A'); // Sorted by title
        });
    }));
});

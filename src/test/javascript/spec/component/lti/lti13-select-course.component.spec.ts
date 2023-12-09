import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { LtiCoursesComponent } from 'app/lti/lti13-select-course.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { LtiCourseCardComponent } from 'app/lti/lti-course-card.component';

describe('LtiCoursesComponent', () => {
    let component: LtiCoursesComponent;
    let fixture: ComponentFixture<LtiCoursesComponent>;
    let courseManagementService: CourseManagementService;

    const mockCourses: Course[] = [
        { id: 1, title: 'Course A', onlineCourse: true },
        { id: 2, title: 'Course B', onlineCourse: false },
        { id: 3, title: 'Course C', onlineCourse: true },
    ];

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [LtiCoursesComponent, MockComponent(LtiCourseCardComponent)],
            providers: [
                MockProvider(CourseManagementService, {
                    findAllForDashboard: jest.fn().mockReturnValue(of(new HttpResponse({ body: mockCourses }))),
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                jest.fn().mockReturnValue(of(new HttpResponse({ body: mockCourses })));
                courseManagementService = TestBed.inject(CourseManagementService);
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

    it('should load and filter courses on ngOnInit', waitForAsync(() => {
        component.ngOnInit();
        fixture.whenStable().then(() => {
            expect(courseManagementService.findAllForDashboard).toHaveBeenCalled();
            expect(component.courses).toHaveLength(2); // Only online courses
            expect(component.courses[0].title).toBe('Course A'); // Sorted by title
        });
    }));
});

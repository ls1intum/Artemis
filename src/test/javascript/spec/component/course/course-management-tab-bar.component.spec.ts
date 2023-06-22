import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { CourseManagementTabBarComponent } from 'app/course/manage/course-management-tab-bar/course-management-tab-bar.component';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseAdminService } from 'app/course/manage/course-admin.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { ArtemisTestModule } from '../../test.module';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';

describe('Course Management Tab Bar Component', () => {
    let component: CourseManagementTabBarComponent;
    let componentFixture: ComponentFixture<CourseManagementTabBarComponent>;

    let courseManagementService: CourseManagementService;
    let courseAdminService: CourseAdminService;
    let eventManager: EventManager;

    const course: Course = {
        id: 42,
        title: 'Course Title',
        description: 'Course description',
        isAtLeastInstructor: true,
        courseArchivePath: 'some-path',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                CourseManagementTabBarComponent,
                MockComponent(HeaderCourseComponent),
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(FeatureToggleLinkDirective),
            ],
            imports: [HttpClientTestingModule, RouterModule, ArtemisTestModule],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        firstChild: {
                            params: of({ courseId: course.id }),
                        },
                    },
                },
                MockProvider(CourseManagementService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseManagementTabBarComponent);
                component = componentFixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                courseAdminService = TestBed.inject(CourseAdminService);
                eventManager = TestBed.inject(EventManager);
            });
    });

    beforeEach(fakeAsync(() => {
        const statsStub = jest.spyOn(courseManagementService, 'find');
        statsStub.mockReturnValue(of(new HttpResponse({ body: course })));
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should register changes in course on init', () => {
        componentFixture.detectChanges();
        component.ngOnInit();

        expect(component.course).toEqual(course);
    });

    it('should destroy event subscriber onDestroy', () => {
        const destroySpy = jest.spyOn(eventManager, 'destroy');
        component.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
    });

    it('should broadcast course modification on delete', () => {
        const broadcastSpy = jest.spyOn(eventManager, 'broadcast');
        const deleteStub = jest.spyOn(courseAdminService, 'delete');
        deleteStub.mockReturnValue(of(new HttpResponse<void>()));

        const courseId = 420;
        component.deleteCourse(courseId);

        expect(deleteStub).toHaveBeenCalledWith(courseId);
        expect(broadcastSpy).toHaveBeenCalledWith({
            name: 'courseListModification',
            content: 'Deleted an course',
        });
    });
});

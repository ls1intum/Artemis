import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { CourseManagementTabBarComponent } from 'app/course/manage/course-management-tab-bar/course-management-tab-bar.component';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseAdminService } from 'app/course/manage/course-admin.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { ArtemisTestModule } from '../../test.module';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';

describe('Course Management Tab Bar Component', () => {
    let component: CourseManagementTabBarComponent;
    let componentFixture: ComponentFixture<CourseManagementTabBarComponent>;

    let courseManagementService: CourseManagementService;
    let courseAdminService: CourseAdminService;
    let eventManager: EventManager;
    let courseAccessStorageService: CourseAccessStorageService;

    const router = new MockRouter();
    router.setUrl('');

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
                MockDirective(FeatureToggleHideDirective),
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
                { provide: Router, useValue: router },
                MockProvider(CourseManagementService),
                MockProvider(CourseAccessStorageService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseManagementTabBarComponent);
                component = componentFixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                courseAdminService = TestBed.inject(CourseAdminService);
                eventManager = TestBed.inject(EventManager);
                courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
            });
    });

    beforeEach(fakeAsync(() => {
        const statsStub = jest.spyOn(courseManagementService, 'find');
        statsStub.mockReturnValue(of(new HttpResponse({ body: course })));
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should register changes in course and notify courseAccessStorageService on init', () => {
        const spy = jest.spyOn(courseAccessStorageService, 'onCourseAccessed');

        componentFixture.detectChanges();
        component.ngOnInit();

        expect(component.course).toEqual(course);
        expect(spy).toHaveBeenCalledWith(course.id);
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

    it('should correctly highlight tutorial link', () => {
        // tutorial groups management
        testFuncReturnValueForLink('course-management/1/tutorial-groups', true, () => component.shouldHighlightTutorialsLink());

        // tutorial groups checklist
        testFuncReturnValueForLink('course-management/1/tutorial-groups-checklist', true, () => component.shouldHighlightTutorialsLink());

        // tutorial groups configuration
        testFuncReturnValueForLink('course-management/1/create-tutorial-groups-configuration', true, () => component.shouldHighlightTutorialsLink());

        // other link (should not highlight)
        testFuncReturnValueForLink('course-management/1/exams', false, () => component.shouldHighlightTutorialsLink());
    });

    it('should correctly highlight assessment link', () => {
        // assessment dashboard
        testFuncReturnValueForLink('course-management/1/assessment-dashboard', true, () => component.shouldHighlightAssessmentLink());

        // grading key
        testFuncReturnValueForLink('course-management/1/grading-system', true, () => component.shouldHighlightAssessmentLink());

        // plagiarism cases
        testFuncReturnValueForLink('course-management/1/plagiarism-cases', true, () => component.shouldHighlightAssessmentLink());

        // exam assessment dashboard (should not highlight)
        testFuncReturnValueForLink('course-management/1/exams/1/assessment-dashboard', false, () => component.shouldHighlightAssessmentLink());

        // exam grading key (should not highlight)
        testFuncReturnValueForLink('course-management/1/exams/1/grading-system', false, () => component.shouldHighlightAssessmentLink());
    });

    it('should correctly display control buttons', () => {
        // course management dashboard
        testFuncReturnValueForLink('course-management/1', true, () => component.shouldShowControlButtons());

        // course edit page
        testFuncReturnValueForLink('course-management/1/edit', true, () => component.shouldShowControlButtons());

        // other link (should not highlight)
        testFuncReturnValueForLink('course-management/1/exams', false, () => component.shouldShowControlButtons());
    });

    function testFuncReturnValueForLink(link: string, expected: boolean, func: () => boolean) {
        router.setUrl(link);
        componentFixture.detectChanges();
        expect(func()).toBe(expected);
    }
});

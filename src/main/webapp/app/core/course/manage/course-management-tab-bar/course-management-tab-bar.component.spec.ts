import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { CourseManagementTabBarComponent } from 'app/core/course/manage/course-management-tab-bar/course-management-tab-bar.component';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseAdminService } from 'app/core/course/manage/services/course-admin.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HeaderCourseComponent } from 'app/core/course/manage/header-course/header-course.component';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/buttons/course-exam-archive-button/course-exam-archive-button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';

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
            imports: [RouterModule],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(EventManager),
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
        expect(spy).toHaveBeenCalledWith(course.id, CourseAccessStorageService.STORAGE_KEY, CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW);
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

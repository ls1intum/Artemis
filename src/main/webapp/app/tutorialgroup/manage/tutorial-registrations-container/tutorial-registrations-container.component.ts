import { Component, computed, effect, inject } from '@angular/core';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { ActivatedRoute } from '@angular/router';
import { TutorialRegistrationsComponent } from 'app/tutorialgroup/manage/tutorial-registrations/tutorial-registrations.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/manage/service/tutorial-group-registered-students.service';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-tutorial-registrations-container',
    imports: [TutorialRegistrationsComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-registrations-container.component.html',
})
export class TutorialRegistrationsContainerComponent {
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupCourseAndGroupService = inject(TutorialGroupCourseAndGroupService);
    private accountService = inject(AccountService);
    private tutorialGroupRegisteredStudentsService = inject(TutorialGroupRegisteredStudentsService);
    private tutorialGroup = this.tutorialGroupCourseAndGroupService.tutorialGroup;
    private isTutorialGroupLoading = this.tutorialGroupCourseAndGroupService.isTutorialGroupLoading;
    private course = this.tutorialGroupCourseAndGroupService.course;
    private isCourseLoading = this.tutorialGroupCourseAndGroupService.isCourseLoading;

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    registeredStudents = this.tutorialGroupRegisteredStudentsService.registeredStudents;
    isRegisteredStudentsLoading = this.tutorialGroupRegisteredStudentsService.isLoading;
    loggedInUserIsAtLeastTutorOfGroup = computed(() => this.computeIfLoggedInUserIsAtLeastTutorOfGroup());
    loggedInUserIsAtLeastInstructorInCourse = computed(() => this.computeIfLoggedInUserIsAtLeastInstructorInCourse());
    isLoading = computed(() => this.isTutorialGroupLoading() || this.isCourseLoading() || this.isRegisteredStudentsLoading());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.tutorialGroupRegisteredStudentsService.fetchRegisteredStudents(courseId, tutorialGroupId);

                const fetchTutorialGroupIsNecessary = this.tutorialGroup() === undefined;
                if (fetchTutorialGroupIsNecessary) {
                    this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
                }
            }
        });

        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                const fetchCourseIsNecessary = this.course() === undefined;
                if (fetchCourseIsNecessary) {
                    this.tutorialGroupCourseAndGroupService.fetchCourse(courseId);
                }
            }
        });
    }

    private computeIfLoggedInUserIsAtLeastTutorOfGroup(): boolean | undefined {
        const tutorialGroup = this.tutorialGroup();
        const course = this.course();
        if (!tutorialGroup || !course) return undefined;
        return tutorialGroup.tutorLogin === this.accountService.userIdentity()?.login || this.accountService.isAtLeastEditorInCourse(course);
    }

    private computeIfLoggedInUserIsAtLeastInstructorInCourse(): boolean | undefined {
        const course = this.course();
        if (!course) return undefined;
        return this.accountService.isAtLeastInstructorInCourse(course);
    }
}

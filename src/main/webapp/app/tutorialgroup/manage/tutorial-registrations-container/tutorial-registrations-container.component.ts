import { Component, computed, effect, inject } from '@angular/core';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { ActivatedRoute } from '@angular/router';
import { TutorialRegistrationsComponent } from 'app/tutorialgroup/manage/tutorial-registrations/tutorial-registrations.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/shared/service/tutorial-group-registered-students.service';
import { TutorialGroupSharedStateService } from 'app/tutorialgroup/manage/service/tutorial-group-shared-state.service';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-tutorial-registrations-container',
    imports: [TutorialRegistrationsComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-registrations-container.component.html',
})
export class TutorialRegistrationsContainerComponent {
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupSharedStateService = inject(TutorialGroupSharedStateService);
    private accountService = inject(AccountService);
    private tutorialGroupRegisteredStudentsService = inject(TutorialGroupRegisteredStudentsService);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    isLoading = this.tutorialGroupRegisteredStudentsService.isLoading;
    registeredStudents = this.tutorialGroupRegisteredStudentsService.registeredStudents;
    loggedInUserIsAtLeastTutorOfGroup = computed(() => this.computeIfLoggedInUserIsAtLeastTutorOfGroup());
    loggedInUserIsAtLeastInstructorInCourse = computed(() => this.computeIfLoggedInUserIsAtLeastInstructorInCourse());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupsId = this.tutorialGroupId();
            if (courseId && tutorialGroupsId) {
                this.tutorialGroupRegisteredStudentsService.fetchRegisteredStudents(courseId, tutorialGroupsId);
                this.fetchTutorialGroupIfNecessary(courseId, tutorialGroupsId);
            }
        });

        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.fetchCourseIfNecessary(courseId);
            }
        });
    }

    private computeIfLoggedInUserIsAtLeastTutorOfGroup(): boolean | undefined {
        const tutorialGroup = this.tutorialGroupSharedStateService.tutorialGroup();
        const course = this.tutorialGroupSharedStateService.course();
        if (!tutorialGroup || !course) return undefined;
        return tutorialGroup.tutorLogin === this.accountService.userIdentity()?.login || this.accountService.isAtLeastEditorInCourse(course);
    }

    private computeIfLoggedInUserIsAtLeastInstructorInCourse(): boolean | undefined {
        const course = this.tutorialGroupSharedStateService.course();
        if (!course) return undefined;
        return this.accountService.isAtLeastInstructorInCourse(course);
    }

    private fetchCourseIfNecessary(courseId: number) {
        if (!this.tutorialGroupSharedStateService.course()) {
            this.tutorialGroupSharedStateService.fetchCourse(courseId);
        }
    }

    private fetchTutorialGroupIfNecessary(courseId: number, tutorialGroupId: number) {
        if (!this.tutorialGroupSharedStateService.tutorialGroup()) {
            this.tutorialGroupSharedStateService.fetchTutorialGroup(courseId, tutorialGroupId);
        }
    }
}

import { Component, computed, effect, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TutorialGroupDetailComponent } from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { TutorialGroupSharedStateService } from 'app/tutorialgroup/shared/service/tutorial-group-shared-state.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { AccountService } from 'app/core/auth/account.service';
import { isMessagingEnabled } from 'app/core/course/shared/entities/course.model';

@Component({
    selector: 'jhi-course-tutorial-group-detail-container',
    templateUrl: './course-tutorial-group-detail-container.component.html',
    imports: [TutorialGroupDetailComponent, LoadingIndicatorOverlayComponent],
})
export class CourseTutorialGroupDetailContainerComponent {
    private route = inject(ActivatedRoute);
    private tutorialGroupSharedStateService = inject(TutorialGroupSharedStateService);
    private accountService = inject(AccountService);
    private tutorialGroupId = getNumericPathVariableSignal(this.route, 'tutorialGroupId');

    isLoading = this.tutorialGroupSharedStateService.isTutorialGroupLoading;
    tutorialGroup = this.tutorialGroupSharedStateService.tutorialGroup;
    courseId = getNumericPathVariableSignal(this.route, 'courseId', 2);
    isMessagingEnabled = computed(() => this.computeIfMessagingEnabled());
    loggedInUserIsAtLeastTutorOfGroup = computed(() => this.computeIfLoggedInUserIsAtLeastTutorOfGroup());
    loggedInUserIsAtLeastEditorInCourse = computed(() => this.computeIfLoggedInUserIsAtLeastEditorInCourse());
    loggedInUserIsAtLeastInstructorInCourse = computed(() => this.computeIfLoggedInUserIsAtLeastInstructorInCourse());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.tutorialGroupSharedStateService.fetchCourse(courseId);
            }
        });

        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.tutorialGroupSharedStateService.fetchTutorialGroup(courseId, tutorialGroupId);
            }
        });
    }

    private computeIfMessagingEnabled(): boolean | undefined {
        const course = this.tutorialGroupSharedStateService.course();
        return isMessagingEnabled(course);
    }

    private computeIfLoggedInUserIsAtLeastTutorOfGroup(): boolean | undefined {
        const tutorialGroup = this.tutorialGroupSharedStateService.tutorialGroup();
        const course = this.tutorialGroupSharedStateService.course();
        if (!tutorialGroup || !course) return undefined;
        return tutorialGroup.tutorLogin === this.accountService.userIdentity()?.login || this.accountService.isAtLeastEditorInCourse(course);
    }

    private computeIfLoggedInUserIsAtLeastEditorInCourse(): boolean | undefined {
        const course = this.tutorialGroupSharedStateService.course();
        if (!course) return undefined;
        return this.accountService.isAtLeastEditorInCourse(course);
    }

    private computeIfLoggedInUserIsAtLeastInstructorInCourse(): boolean | undefined {
        const course = this.tutorialGroupSharedStateService.course();
        if (!course) return undefined;
        return this.accountService.isAtLeastInstructorInCourse(course);
    }
}

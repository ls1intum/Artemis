import { Component, computed, effect, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TutorialGroupDetailAccessLevel, TutorialGroupDetailComponent } from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { TutorialGroupSharedStateService } from 'app/tutorialgroup/shared/service/tutorial-group-shared-state.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { isMessagingEnabled } from 'app/core/course/shared/entities/course.model';

@Component({
    selector: 'jhi-course-tutorial-group-detail-container',
    templateUrl: './course-tutorial-group-detail-container.component.html',
    imports: [TutorialGroupDetailComponent, LoadingIndicatorOverlayComponent],
})
export class CourseTutorialGroupDetailContainerComponent {
    protected readonly TutorialGroupDetailManagementAccessLevel = TutorialGroupDetailAccessLevel;
    private route = inject(ActivatedRoute);
    private tutorialGroupSharedStateService = inject(TutorialGroupSharedStateService);
    private tutorialGroupId = getNumericPathVariableSignal(this.route, 'tutorialGroupId');

    isLoading = this.tutorialGroupSharedStateService.isTutorialGroupLoading;
    tutorialGroup = this.tutorialGroupSharedStateService.tutorialGroup;
    courseId = getNumericPathVariableSignal(this.route, 'courseId', 2);
    isMessagingEnabled = computed(() => this.computeIfMessagingEnabled());

    constructor() {
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
}

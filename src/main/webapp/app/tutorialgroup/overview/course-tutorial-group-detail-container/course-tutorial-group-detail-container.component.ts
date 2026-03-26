import { Component, computed, effect, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TutorialGroupDetailAccessLevel, TutorialGroupDetailComponent } from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
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
    private tutorialGroupCourseAndGroupService = inject(TutorialGroupCourseAndGroupService);
    private tutorialGroupId = getNumericPathVariableSignal(this.route, 'tutorialGroupId');
    private course = this.tutorialGroupCourseAndGroupService.course;

    courseId = getNumericPathVariableSignal(this.route, 'courseId');
    tutorialGroup = this.tutorialGroupCourseAndGroupService.tutorialGroup;
    isMessagingEnabled = computed(() => isMessagingEnabled(this.course()));
    isLoading = computed(() => this.tutorialGroupCourseAndGroupService.isTutorialGroupLoading() || this.tutorialGroupCourseAndGroupService.isCourseLoading());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.tutorialGroupCourseAndGroupService.fetchCourse(courseId);
            }
        });

        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
            }
        });
    }
}

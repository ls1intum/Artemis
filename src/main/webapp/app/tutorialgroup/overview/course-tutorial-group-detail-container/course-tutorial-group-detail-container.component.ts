import { Component, computed, effect, inject, signal } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { TutorialGroupDetailComponent } from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { TutorialGroupService } from 'app/tutorialgroup/shared/service/tutorial-group.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';

@Component({
    selector: 'jhi-course-tutorial-group-detail-container',
    templateUrl: './course-tutorial-group-detail-container.component.html',
    imports: [TutorialGroupDetailComponent, LoadingIndicatorOverlayComponent],
})
export class CourseTutorialGroupDetailContainerComponent {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private courseManagementService = inject(CourseManagementService);
    private tutorialGroupService = inject(TutorialGroupService);
    private courseId = getNumericPathVariableSignal(this.route, 'courseId', 2);
    private tutorialGroupId = getNumericPathVariableSignal(this.route, 'tutorialGroupId');

    isTutorialGroupLoading = this.tutorialGroupService.isLoading;
    tutorialGroup = this.tutorialGroupService.tutorialGroup;
    isCourseLoading = signal(false);
    course = signal<Course | undefined>(undefined);
    isLoading = computed<boolean>(() => this.isCourseLoading() || this.isTutorialGroupLoading());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.isCourseLoading.set(true);
                this.courseManagementService.find(courseId).subscribe({
                    next: (response) => {
                        const course = response.body;
                        if (course) {
                            this.course.set(course);
                        }
                        this.isCourseLoading.set(false);
                    },
                    error: () => {
                        this.alertService.addErrorAlert('Something went wrong while fetching the course information for the tutorial group. Please refresh the page to try again.'); // TODO: create string key
                        this.isCourseLoading.set(false);
                    },
                });
            }
        });

        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.tutorialGroupService.fetchTutorialGroupDTO(courseId, tutorialGroupId);
            }
        });
    }
}

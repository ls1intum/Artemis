import { Injectable, inject, signal } from '@angular/core';
import { TutorialGroupDetailDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';

@Injectable({
    providedIn: 'root',
})
export class TutorialGroupSharedStateService {
    private tutorialGroupsService = inject(TutorialGroupsService);
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);

    isTutorialGroupLoading = signal(false);
    tutorialGroup = signal<TutorialGroupDetailDTO | undefined>(undefined);
    isCourseLoading = signal(false);
    course = signal<Course | undefined>(undefined);

    toggleCancellationStatusOfSession(sessionId: number) {
        this.tutorialGroup.update((tutorialGroup) => {
            if (!tutorialGroup) return tutorialGroup;
            return {
                ...tutorialGroup,
                sessions: tutorialGroup.sessions.map((session) => (session.id !== sessionId ? session : { ...session, isCancelled: !session.isCancelled })),
            };
        });
    }

    fetchTutorialGroup(courseId: number, tutorialGroupId: number) {
        this.isTutorialGroupLoading.set(true);
        this.tutorialGroupsService.getTutorialGroupDTO(courseId, tutorialGroupId).subscribe({
            next: (tutorialGroup) => {
                this.tutorialGroup.set(tutorialGroup);
                this.isTutorialGroupLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.services.tutorialGroupSharedStateService.networkError.fetchGroup');
                this.isTutorialGroupLoading.set(false);
            },
        });
    }

    fetchCourse(courseId: number) {
        this.isCourseLoading.set(true);
        this.courseManagementService.find(courseId).subscribe({
            next: (response) => {
                const course = response.body;
                if (course) {
                    this.course.set(course);
                } else {
                    this.alertService.addErrorAlert('artemisApp.services.tutorialGroupSharedStateService.networkError.fetchCourse');
                }
                this.isCourseLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.services.tutorialGroupSharedStateService.networkError.fetchCourse');
                this.isCourseLoading.set(false);
            },
        });
    }
}

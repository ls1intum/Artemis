import { Injectable, inject, signal } from '@angular/core';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { map } from 'rxjs/operators';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';

@Injectable({
    providedIn: 'root',
})
export class TutorialGroupCourseAndGroupService {
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);

    isTutorialGroupLoading = signal(false);
    tutorialGroup = signal<TutorialGroupDetailData | undefined>(undefined);
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

    insertSession(sessionToInsert: TutorialGroupSession) {
        this.tutorialGroup.update((tutorialGroup) => {
            if (!tutorialGroup) return tutorialGroup;

            const sessions = [...tutorialGroup.sessions];
            const existingSessionIndex = sessions.findIndex((session) => session.id === sessionToInsert.id);

            if (existingSessionIndex !== -1) {
                sessions.splice(existingSessionIndex, 1);
            }

            const insertIndex = sessions.findIndex((session) => sessionToInsert.start.isBefore(session.start));
            if (insertIndex === -1) {
                sessions.push(sessionToInsert);
            } else {
                sessions.splice(insertIndex, 0, sessionToInsert);
            }

            return {
                ...tutorialGroup,
                sessions,
            };
        });
    }

    fetchTutorialGroup(courseId: number, tutorialGroupId: number) {
        this.isTutorialGroupLoading.set(true);
        this.tutorialGroupApiService
            .getTutorialGroup(courseId, tutorialGroupId)
            .pipe(map((tutorialGroupDetail) => new TutorialGroupDetailData(tutorialGroupDetail)))
            .subscribe({
                next: (dto) => {
                    this.tutorialGroup.set(dto);
                    this.isTutorialGroupLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchGroup');
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
                    this.alertService.addErrorAlert('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchCourse');
                }
                this.isCourseLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchCourse');
                this.isCourseLoading.set(false);
            },
        });
    }
}

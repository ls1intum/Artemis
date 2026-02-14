import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { TutorialGroupScheduleDTO, TutorialGroupTutorDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseGroup } from 'app/core/course/shared/entities/course.model';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { User } from 'app/core/user/user.model';
import { TutorialEditComponent } from 'app/tutorialgroup/manage/tutorial-edit/tutorial-edit.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupService } from 'app/tutorialgroup/shared/service/tutorial-group.service';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-tutorial-edit-container',
    imports: [TutorialEditComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-edit-container.component.html',
    styleUrl: './tutorial-edit-container.component.scss',
})
export class TutorialEditContainerComponent {
    private destroyRef = inject(DestroyRef);
    private activatedRoute = inject(ActivatedRoute);
    private courseManagementService = inject(CourseManagementService);
    private tutorialGroupService = inject(TutorialGroupService);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    isTutorsLoading = signal(false);
    tutors = signal<TutorialGroupTutorDTO[]>([]);
    isTutorialGroupLoading = this.tutorialGroupService.isLoading;
    tutorialGroup = this.tutorialGroupService.tutorialGroup;
    isScheduleLoading = signal(false);
    schedule = signal<TutorialGroupScheduleDTO | undefined>(undefined);
    isLoading = computed<boolean>(() => this.isTutorsLoading() || this.isTutorialGroupLoading() || this.isScheduleLoading());

    constructor() {
        effect(() => this.loadTutors());
        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.loadGroupIfNecessary(courseId, tutorialGroupId);
                this.loadSchedule(courseId, tutorialGroupId);
            }
        });
    }

    private convertUserToTutorialGroupTutorDTO(user: User): TutorialGroupTutorDTO | undefined {
        const id = user.id;
        const login = user.login;
        if (!id || !login) return undefined;
        let nameAndLogin = login;
        const firstName = user.firstName;
        const lastName = user.lastName;
        if (firstName && lastName) {
            nameAndLogin += ` (${firstName} ${lastName})`;
        } else if (firstName) {
            nameAndLogin += ` (${firstName})`;
        } else if (lastName) {
            nameAndLogin += ` (${lastName})`;
        }
        return {
            id: id,
            nameAndLogin: nameAndLogin,
        };
    }

    private loadTutors() {
        const courseId = this.courseId();
        if (courseId) {
            this.isTutorsLoading.set(true);
            this.courseManagementService
                .getAllUsersInCourseGroup(courseId, CourseGroup.TUTORS)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({
                    next: (response: HttpResponse<User[]>) => {
                        const users = response.body ?? [];
                        const tutors: TutorialGroupTutorDTO[] = users.map((user) => this.convertUserToTutorialGroupTutorDTO(user)).filter((tutor) => tutor !== undefined);
                        this.tutors.set(tutors);
                        this.isTutorsLoading.set(false);
                    },
                    error: () => {
                        // TODO: create string key
                        this.alertService.addErrorAlert('Something went wrong while loading the tutor options for the tutorial group. Please try again by refreshing the page.');
                        this.isTutorsLoading.set(false);
                    },
                });
        }
    }

    private loadSchedule(courseId: number, tutorialGroupId: number) {
        this.isScheduleLoading.set(true);
        this.tutorialGroupsService.getTutorialGroupScheduleDTO(courseId, tutorialGroupId).subscribe({
            next: (schedule) => {
                this.schedule.set(schedule);
                this.isScheduleLoading.set(false);
            },
            error: () => {
                // TODO: create string key
                this.alertService.addErrorAlert('Something went wrong while loading the schedule for the tutorial group. Please try again by refreshing the page.');
                this.isScheduleLoading.set(false);
            },
        });
    }

    private loadGroupIfNecessary(courseId: number, tutorialGroupId: number) {
        const tutorialGroup = this.tutorialGroup();
        if (!tutorialGroup) {
            this.tutorialGroupService.fetchTutorialGroupDTO(courseId, tutorialGroupId);
        }
    }
}

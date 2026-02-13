import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { TutorialGroupTutorDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
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

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    isTutorsLoading = signal(false);
    tutors = signal<TutorialGroupTutorDTO[]>([]);
    isTutorialGroupLoading = this.tutorialGroupService.isLoading;
    tutorialGroup = this.tutorialGroupService.tutorialGroup;
    isLoading = computed<boolean>(() => this.isTutorsLoading() || this.isTutorialGroupLoading());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.isTutorsLoading.set(true);
                this.courseManagementService
                    .getAllUsersInCourseGroup(courseId, CourseGroup.TUTORS)
                    .pipe(takeUntilDestroyed(this.destroyRef))
                    .subscribe((response: HttpResponse<User[]>) => {
                        const users = response.body ?? [];
                        const tutors: TutorialGroupTutorDTO[] = users.map((user) => this.convertUserToTutorialGroupTutorDTO(user)).filter((tutor) => tutor !== undefined);
                        this.tutors.set(tutors);
                        this.isTutorsLoading.set(false);
                        // TODO: catch errors
                    });
            }
        });
        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            const tutorialGroup = this.tutorialGroup();
            if (courseId && tutorialGroupId && !tutorialGroup) {
                this.tutorialGroupService.fetchTutorialGroupDTO(courseId, tutorialGroupId);
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
}

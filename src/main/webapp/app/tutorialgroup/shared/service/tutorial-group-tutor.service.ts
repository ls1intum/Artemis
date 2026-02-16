import { Injectable, inject, signal } from '@angular/core';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseGroup } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupTutorDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { AlertService } from 'app/shared/service/alert.service';

@Injectable({ providedIn: 'root' })
export class TutorialGroupTutorService {
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);

    isLoading = signal(false);
    tutors = signal<TutorialGroupTutorDTO[]>([]);

    loadTutors(courseId: number) {
        this.isLoading.set(true);
        this.courseManagementService.getAllUsersInCourseGroup(courseId, CourseGroup.TUTORS).subscribe({
            next: (response: HttpResponse<User[]>) => {
                const users = response.body ?? [];
                const tutors = users.map((u) => this.convertUserToTutorialGroupTutorDTO(u)).filter(Boolean) as TutorialGroupTutorDTO[];

                this.tutors.set(tutors);
                this.isLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert(
                    // TODO: create string key
                    'Something went wrong while loading the tutor options for the tutorial group. Please try again by refreshing the page.',
                );
                this.isLoading.set(false);
            },
        });
    }

    private convertUserToTutorialGroupTutorDTO(user: User): TutorialGroupTutorDTO | undefined {
        if (!user.id || !user.login) return;
        let nameAndLogin = user.login;
        if (user.firstName && user.lastName) {
            nameAndLogin += ` (${user.firstName} ${user.lastName})`;
        } else if (user.firstName) {
            nameAndLogin += ` (${user.firstName})`;
        } else if (user.lastName) {
            nameAndLogin += ` (${user.lastName})`;
        }
        return { id: user.id, nameAndLogin };
    }
}

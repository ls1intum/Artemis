import { Injectable, inject, signal } from '@angular/core';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';

@Injectable({
    providedIn: 'root',
})
export class TutorialGroupRegisteredStudentsService {
    private tutorialGroupsService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private registeredStudentsInternal = signal<TutorialGroupRegisteredStudentDTO[]>([]);

    isLoading = signal(false);
    registeredStudents = this.registeredStudentsInternal.asReadonly();

    deregisterStudent(courseId: number, tutorialGroupId: number, studentLogin: string) {
        this.isLoading.set(true);
        this.tutorialGroupsService.deregisterStudent(courseId, tutorialGroupId, studentLogin).subscribe({
            next: () => {
                this.registeredStudentsInternal.update((registeredStudents) => {
                    return registeredStudents.filter((student) => student.login !== studentLogin);
                });
                this.isLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.services.tutorialGroupRegisteredStudentsService.networkError.deregisterStudent');
                this.isLoading.set(false);
            },
        });
    }

    fetchRegisteredStudents(courseId: number, tutorialGroupId: number) {
        this.isLoading.set(true);
        this.tutorialGroupsService.getRegisteredStudentDTOs(courseId, tutorialGroupId).subscribe({
            next: (registeredStudents) => {
                this.registeredStudentsInternal.set(registeredStudents);
                this.isLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.services.tutorialGroupRegisteredStudentsService.networkError.fetchRegisteredStudents');
                this.isLoading.set(false);
            },
        });
    }

    addStudentsToRegisteredStudentsState(students: TutorialGroupRegisteredStudentDTO[]) {
        this.registeredStudentsInternal.update((registeredStudents) => {
            const existingStudentIds = new Set(registeredStudents.map((student) => student.id));
            const newStudents: TutorialGroupRegisteredStudentDTO[] = students.filter((student) => {
                if (existingStudentIds.has(student.id)) {
                    return false;
                }
                existingStudentIds.add(student.id);
                return true;
            });
            return [...registeredStudents, ...newStudents];
        });
    }
}

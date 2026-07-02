import { Injectable, inject, signal } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { TutorialGroupStudent } from 'app/openapi/models/tutorial-group-student';

@Injectable({
    providedIn: 'root',
})
export class TutorialGroupRegisteredStudentsService {
    private tutorialGroupApiService = inject(TutorialGroupApi);
    private alertService = inject(AlertService);
    private registeredStudentsInternal = signal<TutorialGroupStudent[]>([]);

    isLoading = signal(false);
    registeredStudents = this.registeredStudentsInternal.asReadonly();

    deregisterStudent(courseId: number, tutorialGroupId: number, studentLogin: string) {
        this.isLoading.set(true);
        this.tutorialGroupApiService.deregisterStudent(courseId, tutorialGroupId, studentLogin).subscribe({
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
        this.tutorialGroupApiService.getRegisteredStudents(courseId, tutorialGroupId).subscribe({
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

    addStudentsToRegisteredStudentsState(students: TutorialGroupStudent[]) {
        this.registeredStudentsInternal.update((registeredStudents) => {
            const existingStudentIds = new Set(registeredStudents.map((student) => student.id));
            const newStudents: TutorialGroupStudent[] = students.filter((student) => {
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

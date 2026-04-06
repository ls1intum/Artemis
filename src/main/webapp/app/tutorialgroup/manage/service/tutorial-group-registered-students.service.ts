import { Injectable, inject, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

@Injectable({
    providedIn: 'root',
})
export class TutorialGroupRegisteredStudentsService {
    private tutorialGroupApiService = inject(TutorialGroupApiService);
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

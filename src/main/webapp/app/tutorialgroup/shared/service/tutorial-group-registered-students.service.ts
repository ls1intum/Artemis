import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Injectable({
    providedIn: 'root',
})
export class TutorialGroupRegisteredStudentsService {
    private destroyRef = inject(DestroyRef);
    private tutorialGroupsService = inject(TutorialGroupsService);

    isLoading = signal(false);
    registeredStudents = signal<TutorialGroupRegisteredStudentDTO[]>([]);

    deregisterStudent(courseId: number, tutorialGroupId: number, studentLogin: string) {
        this.isLoading.set(true);
        this.tutorialGroupsService
            .deregisterStudent(courseId, tutorialGroupId, studentLogin)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                this.registeredStudents.update((registeredStudents) => {
                    return registeredStudents.filter((student) => student.login !== studentLogin);
                });
                this.isLoading.set(false);
                // TODO: catch errors
            });
    }

    fetchRegisteredStudents(courseId: number, tutorialGroupId: number) {
        if (!courseId || !tutorialGroupId) {
            return;
        }

        this.isLoading.set(true);
        this.tutorialGroupsService
            .getRegisteredStudentDTOs(courseId, tutorialGroupId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((registeredStudents) => {
                this.registeredStudents.set(registeredStudents);
                this.isLoading.set(false);
                // TODO: catch errors
            });
    }

    addStudentsToRegisteredStudentsState(students: TutorialGroupRegisteredStudentDTO[]) {
        this.registeredStudents.update((registeredStudents) => {
            const newStudents: TutorialGroupRegisteredStudentDTO[] = students.filter((student) => {
                return registeredStudents.every((alreadyRegisteredStudent) => alreadyRegisteredStudent.id !== student.id);
            });
            return [...registeredStudents, ...newStudents];
        });
    }
}

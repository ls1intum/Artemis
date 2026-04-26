import { Component, InputSignal, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CourseGroup } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';

@Component({
    selector: 'jhi-exam-add-students-dialog',
    standalone: true,
    templateUrl: './exam-add-students-dialog.component.html',
    imports: [Dialog, FormsModule, IconFieldModule, InputIconModule, InputTextModule, TableModule, ButtonDirective, TranslateDirective, ArtemisTranslatePipe],
})
export class ExamAddStudentsDialogComponent {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly examManagementService = inject(ExamManagementService);
    private readonly alertService = inject(AlertService);

    readonly courseId: InputSignal<number> = input.required();
    readonly exam: InputSignal<Exam> = input.required();

    dialogVisible = model(false);
    readonly studentsChanged = output<void>();

    readonly searchText = signal('');
    readonly isLoading = signal(false);
    readonly allCourseStudents = signal([] as User[]);

    private readonly currentlyRegisteringLogins = signal(new Set<string>());
    private readonly localRegisteredLogins = signal(new Set<string>()); // helps to show registered status without waiting for parent
    private readonly parentRegisteredLogins = computed(() => {
        const registeredLogins = new Set<string>();

        this.exam()
            .examUsers?.map((examUser) => examUser.user?.login)
            .filter((login): login is string => !!login)
            .forEach((login) => registeredLogins.add(login));

        return registeredLogins;
    });
    readonly registeredLogins = computed(() => new Set([...this.parentRegisteredLogins(), ...this.localRegisteredLogins()]));

    readonly userSearchResults = computed(() => {
        const trimmedSearch = this.searchText().trim().toLowerCase();
        const students = this.allCourseStudents();

        if (!trimmedSearch) {
            return students;
        }

        return students.filter((student) => this.matchesSearch(student, trimmedSearch));
    });
    readonly noSearchResults = computed(() => this.userSearchResults().length === 0);

    constructor() {
        effect(() => {
            this.removeLocalWhichAreInParent();
        });
    }

    private removeLocalWhichAreInParent() {
        const parentRegisteredLogins = this.parentRegisteredLogins();
        this.localRegisteredLogins.update((old) => new Set([...old].filter((login) => !parentRegisteredLogins.has(login))));
    }

    openDialog(): void {
        this.dialogVisible.set(true);
        this.searchText.set('');
        this.loadCourseStudents();
    }

    closeDialog(): void {
        this.dialogVisible.set(false);
    }

    isAlreadyRegistered(student: User): boolean {
        return !!student.login && this.registeredLogins().has(student.login);
    }

    isRegistering(student: User): boolean {
        return !!student.login && this.currentlyRegisteringLogins().has(student.login);
    }

    registerStudent(student: User): void {
        const examId = this.exam().id;
        const login = student.login;
        if (!examId || !login || this.isAlreadyRegistered(student) || this.isRegistering(student)) {
            return;
        }

        this.currentlyRegisteringLogins.update((currentLogins) => this.copyAndAdd(currentLogins, login));

        this.examManagementService.addStudentToExam(this.courseId(), examId, login).subscribe({
            next: () => {
                this.localRegisteredLogins.update((currentLogins) => this.copyAndAdd(currentLogins, login));
                this.currentlyRegisteringLogins.update((currentLogins) => this.copyAndDelete(currentLogins, login));
                this.studentsChanged.emit();
            },
            error: () => {
                this.currentlyRegisteringLogins.update((currentLogins) => this.copyAndDelete(currentLogins, login));
                this.alertService.error('artemisApp.examManagement.examStudents.addDialog.errorRegisterStudent');
            },
        });
    }

    private loadCourseStudents(): void {
        this.isLoading.set(true);

        this.courseManagementService.getAllUsersInCourseGroup(this.courseId(), CourseGroup.STUDENTS).subscribe({
            next: (studentsResponse) => {
                const sortedStudents = (studentsResponse.body ?? [])
                    .filter((student) => !!student.login)
                    .toSorted((studentA, studentB) => (studentA.login ?? '').localeCompare(studentB.login ?? ''));
                this.allCourseStudents.set(sortedStudents);
                this.isLoading.set(false);
            },
            error: () => {
                this.alertService.error('artemisApp.examManagement.examStudents.addDialog.errorSearchStudents');
                this.allCourseStudents.set([]);
                this.isLoading.set(false);
            },
        });
    }

    private matchesSearch(student: User, searchTerm: string): boolean {
        const login = student.login?.toLowerCase() ?? '';
        const name = student.name?.toLowerCase() ?? '';
        const visibleRegistrationNumber = student.visibleRegistrationNumber?.toLowerCase() ?? '';
        const email = student.email?.toLowerCase() ?? '';
        return login.includes(searchTerm) || name.includes(searchTerm) || visibleRegistrationNumber.includes(searchTerm) || email.includes(searchTerm);
    }

    private copyAndAdd<T>(set: Set<T>, element: T): Set<T> {
        const copy = new Set(set);
        copy.add(element);
        return copy;
    }

    private copyAndDelete<T>(set: Set<T>, element: T): Set<T> {
        const copy = new Set(set);
        copy.delete(element);
        return copy;
    }
}

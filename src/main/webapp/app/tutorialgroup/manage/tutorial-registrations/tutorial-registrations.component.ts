import { Component, computed, inject, input, output, signal } from '@angular/core';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { FormsModule } from '@angular/forms';
import { TutorialRegistrationsImportModalComponent } from 'app/tutorialgroup/manage/tutorial-registrations-import-modal/tutorial-registrations-import-modal.component';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';
import { ExportUserInformationRow, exportUserInformationAsCsv } from 'app/shared/user-import/helpers/write-users-to-csv';

export interface DeregisterStudentEvent {
    courseId: number;
    tutorialGroupId: number;
    studentLogin: string;
}

@Component({
    selector: 'jhi-tutorial-registrations',
    imports: [
        IconFieldModule,
        InputIconModule,
        InputTextModule,
        ConfirmDialogModule,
        ButtonModule,
        ProfilePictureComponent,
        TranslateDirective,
        FormsModule,
        TutorialRegistrationsImportModalComponent,
    ],
    providers: [ConfirmationService],
    templateUrl: './tutorial-registrations.component.html',
    styleUrl: './tutorial-registrations.component.scss',
})
export class TutorialRegistrationsComponent {
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private confirmationService = inject(ConfirmationService);
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    registeredStudents = input.required<TutorialGroupRegisteredStudentDTO[]>();
    filteredRegisteredStudents = computed<TutorialGroupRegisteredStudentDTO[]>(() => this.computeFilteredRegisteredStudents());
    onDeregisterStudent = output<DeregisterStudentEvent>();
    searchFieldPlaceholder = computed<string>(() => this.computeSearchFieldPlaceholder());
    searchString = signal('');
    onStudentsRegistered = output<void>();

    confirmDeregistration(event: Event, studentLogin: string) {
        this.confirmationService.confirm({
            target: event.target as EventTarget,
            message: this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.removeStudentButton.confirmationDialogue.message'),
            header: this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.removeStudentButton.confirmationDialogue.header'),
            rejectButtonProps: {
                label: this.translateService.instant('entity.action.cancel'),
                severity: 'secondary',
            },
            acceptButtonProps: {
                label: this.translateService.instant('entity.action.remove'),
                severity: 'danger',
            },
            accept: () => {
                this.onDeregisterStudent.emit({
                    courseId: this.courseId(),
                    tutorialGroupId: this.tutorialGroupId(),
                    studentLogin: studentLogin,
                });
            },
        });
    }

    exportRegisteredStudents() {
        const registeredStudents = this.registeredStudents();
        if (registeredStudents.length > 0) {
            const rows: ExportUserInformationRow[] = registeredStudents.map((student) => {
                return {
                    [NAME_KEY]: student.name?.trim() ?? '',
                    [USERNAME_KEY]: student.login?.trim() ?? '',
                    [EMAIL_KEY]: student.email?.trim() ?? '',
                    [REGISTRATION_NUMBER_KEY]: student.registrationNumber?.trim() ?? '',
                };
            });
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
            exportUserInformationAsCsv(rows, keys, 'registrations');
        }
    }

    private computeSearchFieldPlaceholder(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.searchFieldPlaceholder');
    }

    private computeFilteredRegisteredStudents(): TutorialGroupRegisteredStudentDTO[] {
        const registeredStudents = this.registeredStudents();
        const searchString = this.searchString().toLowerCase();
        if (searchString === '') return registeredStudents;
        return registeredStudents.filter((student) => {
            const nameMatches = student.name !== undefined && student.name.toLowerCase().includes(searchString);
            const loginMatches = student.login.toLowerCase().includes(searchString);
            return nameMatches || loginMatches;
        });
    }
}

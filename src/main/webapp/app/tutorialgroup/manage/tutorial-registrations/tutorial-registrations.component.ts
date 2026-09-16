import { Component, computed, inject, input, signal } from '@angular/core';
import { faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonDirective, TumUiConfirmDialogComponent, TumUiConfirmationService, TumUiIconFieldComponent, TumUiInputDirective } from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { FormsModule } from '@angular/forms';
import { TutorialRegistrationsImportModalComponent } from 'app/tutorialgroup/manage/tutorial-registrations-import-modal/tutorial-registrations-import-modal.component';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';
import { ExportUserInformationRow, exportUserInformationAsCsv } from 'app/shared-ui/user-import/util/write-users-to-csv';
import { TutorialRegistrationsRegisterModalComponent } from 'app/tutorialgroup/manage/tutorial-registrations-register-modal/tutorial-registrations-register-modal.component';
import {
    TutorialRegistrationsStudentsTableComponent,
    TutorialRegistrationsStudentsTableRemoveActionColumnInfo,
} from 'app/tutorialgroup/manage/tutorial-registrations-students-table/tutorial-registrations-students-table.component';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/manage/service/tutorial-group-registered-students.service';
import { TutorialGroupStudent } from 'app/openapi/model/tutorial-group-student';

@Component({
    selector: 'jhi-tutorial-registrations',
    imports: [
        TumUiButtonDirective,
        TumUiConfirmDialogComponent,
        TumUiIconFieldComponent,
        TumUiInputDirective,
        TranslateDirective,
        FormsModule,
        TutorialRegistrationsImportModalComponent,
        TutorialRegistrationsRegisterModalComponent,
        TutorialRegistrationsStudentsTableComponent,
    ],
    providers: [TumUiConfirmationService],
    templateUrl: './tutorial-registrations.component.html',
    styleUrl: './tutorial-registrations.component.scss',
})
export class TutorialRegistrationsComponent {
    private confirmationService = inject(TumUiConfirmationService);
    private translateService = inject(TranslateService);
    private tutorialGroupRegisteredStudentService = inject(TutorialGroupRegisteredStudentsService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    protected readonly faMagnifyingGlass = faMagnifyingGlass;

    readonly studentsTableRemoveActionColumnInfo: TutorialRegistrationsStudentsTableRemoveActionColumnInfo = {
        headerStringKey: 'artemisApp.pages.tutorialGroupRegistrations.studentsTableHeaderLabel.deregister',
        onRemove: (_event, student) => this.confirmDeregistration(student),
    };

    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    registeredStudents = input.required<TutorialGroupStudent[]>();
    loggedInUserIsAtLeastTutorOfGroup = input.required<boolean>();
    loggedInUserIsAtLeastInstructorInCourse = input.required<boolean>();
    searchFieldPlaceholder = computed<string>(() => this.computeSearchFieldPlaceholder());
    searchString = signal('');
    filteredRegisteredStudents = computed<TutorialGroupStudent[]>(() => this.computeFilteredRegisteredStudents());

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

    private confirmDeregistration(student: TutorialGroupStudent) {
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.removeStudentButton.confirmationDialogue.header'),
            message: this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.removeStudentButton.confirmationDialogue.message'),
            acceptLabel: this.translateService.instant('entity.action.remove'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptSeverity: 'danger',
            accept: () => {
                this.tutorialGroupRegisteredStudentService.deregisterStudent(this.courseId(), this.tutorialGroupId(), student.login);
            },
        });
    }

    private computeSearchFieldPlaceholder(): string {
        this.currentLocale();
        return this.translateService.instant('artemisApp.pages.tutorialGroupRegistrations.searchFieldPlaceholder');
    }

    private computeFilteredRegisteredStudents(): TutorialGroupStudent[] {
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

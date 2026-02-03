import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { ButtonDirective } from 'primeng/button';
import { readStudentDTOsFromCSVFile } from 'app/shared/user-import/helpers/read-users-from-csv';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import {
    TutorialRegistrationsImportModalTableComponent,
    TutorialRegistrationsImportModalTableRow,
} from 'app/tutorialgroup/manage/tutorial-registrations-import-modal-table/tutorial-registrations-import-modal-table.component';
import { TutorialGroupRegisterStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';

export enum ImportFlowStep {
    EXPLANATION = 'EXPLANATION',
    CONFIRMATION = 'CONFIRMATION',
    RESULTS = 'RESULTS',
}

interface ImportResult {
    student: TutorialGroupRegisterStudentDTO;
    exists: boolean;
}

@Component({
    selector: 'jhi-tutorial-registrations-import-modal',
    imports: [DialogModule, TranslateDirective, ButtonDirective, TutorialRegistrationsImportModalTableComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-registrations-import-modal.component.html',
    styleUrl: './tutorial-registrations-import-modal.component.scss',
})
export class TutorialRegistrationsImportModalComponent {
    protected readonly ImportFlowStep = ImportFlowStep;

    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private parsedStudents = signal<TutorialGroupRegisterStudentDTO[]>([]);
    private importResults = signal<ImportResult[]>([]);

    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    isLoading = signal(false);
    isOpen = signal(false);
    flowStep = signal<ImportFlowStep>(ImportFlowStep.EXPLANATION);
    header = computed<string>(() => this.computeHeader());
    tableRows = computed<TutorialRegistrationsImportModalTableRow[]>(() => this.computeTableRows());
    allStudentsExist = computed<boolean>(() => this.importResults().every((student) => student.exists));
    noStudentsExist = computed<boolean>(() => this.importResults().every((student) => !student.exists));
    onStudentsRegistered = output<void>();

    open() {
        this.parsedStudents.set([]);
        this.importResults.set([]);
        this.flowStep.set(ImportFlowStep.EXPLANATION);
        this.isOpen.set(true);
    }

    close() {
        this.isOpen.set(false);
    }

    async parseStudents(event: Event) {
        this.isLoading.set(true);
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];

        if (!file) {
            return;
        }

        const result = await readStudentDTOsFromCSVFile(file);
        if (!result.ok) {
            this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupRegistrations.importModal.invalidFileEntriesAlert');
            return;
        }

        const parsedStudents: TutorialGroupRegisterStudentDTO[] = result.students.map((student) => {
            return { login: student.login, registrationNumber: student.registrationNumber };
        });
        if (parsedStudents.length === 0) {
            this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupRegistrations.importModal.noFileEntriesAlert');
            return;
        }

        this.isLoading.set(false);
        this.parsedStudents.set(parsedStudents);
        this.flowStep.set(ImportFlowStep.CONFIRMATION);
    }

    importParsedStudents() {
        this.isLoading.set(true);
        this.tutorialGroupsService.registerMultipleStudents(this.courseId(), this.tutorialGroupId(), this.parsedStudents()).subscribe({
            next: (response: HttpResponse<Array<TutorialGroupRegisterStudentDTO>>) => {
                const nonExistingStudents = response.body || [];
                const studentResults: ImportResult[] = this.parsedStudents().map((parsedStudent) => {
                    return {
                        student: parsedStudent,
                        exists: !nonExistingStudents.some((nonExistingStudent) => {
                            if (parsedStudent.login && nonExistingStudent.login) {
                                return nonExistingStudent.login === parsedStudent.login;
                            }
                            if (parsedStudent.registrationNumber && nonExistingStudent.registrationNumber) {
                                return nonExistingStudent.registrationNumber === parsedStudent.registrationNumber;
                            }
                            return false;
                        }),
                    };
                });
                this.importResults.set(studentResults);
                this.flowStep.set(ImportFlowStep.RESULTS);
                const someStudentsRegistered = studentResults.some((student) => student.exists);
                if (someStudentsRegistered) {
                    this.onStudentsRegistered.emit();
                }
                this.isLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupRegistrations.importModal.importErrorAlert');
                this.isLoading.set(false);
            },
        });
    }

    goToExplanationStep() {
        this.parsedStudents.set([]);
        this.flowStep.set(ImportFlowStep.EXPLANATION);
    }

    private computeHeader(): string {
        this.currentLocale();
        const headerKey = this.getHeaderKeyFor(this.flowStep());
        return this.translateService.instant(headerKey);
    }

    private getHeaderKeyFor(flowState: ImportFlowStep): string {
        switch (flowState) {
            case ImportFlowStep.EXPLANATION:
                return 'artemisApp.pages.tutorialGroupRegistrations.importModal.explanationHeader';
            case ImportFlowStep.CONFIRMATION:
                return 'artemisApp.pages.tutorialGroupRegistrations.importModal.confirmImportHeader';
            case ImportFlowStep.RESULTS:
                return 'artemisApp.pages.tutorialGroupRegistrations.importModal.importResultsHeader';
        }
    }

    private computeTableRows(): TutorialRegistrationsImportModalTableRow[] {
        switch (this.flowStep()) {
            case ImportFlowStep.EXPLANATION:
                return [
                    { login: 'user_1', registrationNumber: undefined, markFilledCells: false },
                    { login: undefined, registrationNumber: 'ge86vox', markFilledCells: false },
                ];
            case ImportFlowStep.CONFIRMATION:
                return this.parsedStudents().map((student) => {
                    return {
                        login: student.login,
                        registrationNumber: student.registrationNumber,
                        markFilledCells: false,
                    };
                });
            case ImportFlowStep.RESULTS:
                return this.importResults().map((result) => {
                    return {
                        login: result.student.login,
                        registrationNumber: result.student.registrationNumber,
                        markFilledCells: !result.exists,
                    };
                });
        }
    }
}

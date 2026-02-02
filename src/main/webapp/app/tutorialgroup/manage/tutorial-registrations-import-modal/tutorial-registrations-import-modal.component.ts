import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { ButtonDirective } from 'primeng/button';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { readStudentDTOsFromCSVFile } from 'app/shared/user-import/helpers/read-users-from-csv';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpResponse } from '@angular/common/http';
import { Student } from 'app/openapi/model/student';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialRegistrationsImportModalTableRow } from 'app/tutorialgroup/manage/tutorial-registrations-import-modal-table/tutorial-registrations-import-modal-table.component';

export enum ImportFlowStep {
    EXPLANATION = 'EXPLANATION',
    CONFIRMATION = 'CONFIRMATION',
    RESULTS = 'RESULTS',
}

interface ResultStudent {
    login?: string;
    registrationNumber?: string;
    exists: boolean;
}

@Component({
    selector: 'jhi-tutorial-registrations-import-modal',
    imports: [DialogModule, TranslateDirective, ButtonDirective],
    templateUrl: './tutorial-registrations-import-modal.component.html',
    styleUrl: './tutorial-registrations-import-modal.component.scss',
})
export class TutorialRegistrationsImportModalComponent {
    protected readonly ImportFlowStep = ImportFlowStep;

    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    courseId = input.required<number>();
    tutorialGroupId = input.required<number>();
    loading = signal(false);
    isOpen = signal(false);
    flowStep = signal<ImportFlowStep>(ImportFlowStep.EXPLANATION);
    parsedStudents = signal<StudentDTO[]>([]);
    resultStudents = signal<ResultStudent[]>([]);
    header = computed<string>(() => this.computeHeader());
    someStudentsDoNotExist = computed<boolean>(() => this.resultStudents().some((student) => !student.exists));
    noStudentsExist = computed<boolean>(() => this.resultStudents().every((student) => !student.exists));
    onStudentsRegistered = output<void>();
    tableRows = computed<TutorialRegistrationsImportModalTableRow[]>(() => this.computeTableRows());

    open() {
        this.parsedStudents.set([]);
        this.resultStudents.set([]);
        this.flowStep.set(ImportFlowStep.EXPLANATION);
        this.isOpen.set(true);
    }

    close() {
        this.isOpen.set(false);
    }

    async onFileSelected(event: Event) {
        this.loading.set(true);
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

        const parsedStudents = result.students;
        if (parsedStudents.length === 0) {
            this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupRegistrations.importModal.noFileEntriesAlert');
            return;
        }

        this.loading.set(false);
        this.parsedStudents.set(parsedStudents);
        this.flowStep.set(ImportFlowStep.CONFIRMATION);
    }

    importParsedStudents() {
        this.tutorialGroupsService.registerMultipleStudents(this.courseId(), this.tutorialGroupId(), this.parsedStudents()).subscribe({
            next: (response: HttpResponse<Array<Student>>) => {
                const nonExistingStudents = response.body || [];
                const resultStudents: ResultStudent[] = this.parsedStudents().map((parsedStudent) => {
                    return {
                        login: parsedStudent.login,
                        registrationNumber: parsedStudent.registrationNumber,
                        exists: !nonExistingStudents.some(
                            (nonExistingStudent) => nonExistingStudent.login === parsedStudent.login || nonExistingStudent.registrationNumber === parsedStudent.registrationNumber,
                        ),
                    };
                });
                this.resultStudents.set(resultStudents);
                this.flowStep.set(ImportFlowStep.RESULTS);
                const someStudentsRegistered = resultStudents.some((student) => student.exists);
                if (someStudentsRegistered) {
                    this.onStudentsRegistered.emit();
                }
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupRegistrations.importModal.importErrorAlert');
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
                    { login: 'user_1', registrationNumber: undefined, markEmptyCells: false },
                    { login: undefined, registrationNumber: 'ge86vox', markEmptyCells: false },
                ];
            case ImportFlowStep.CONFIRMATION:
                return this.parsedStudents().map((student) => ({ login: student.login, registrationNumber: student.registrationNumber, markEmptyCells: false }));
            case ImportFlowStep.RESULTS:
                return this.resultStudents().map((student) => ({ login: student.login, registrationNumber: student.registrationNumber, markEmptyCells: true }));
        }
    }
}

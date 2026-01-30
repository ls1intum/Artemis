import { Component, computed, inject, signal } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { ButtonDirective } from 'primeng/button';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { readStudentDTOsFromCSVFile } from 'app/shared/user-import/helpers/read-users-from-csv';
import { AlertService } from 'app/shared/service/alert.service';

enum ImportFlowStep {
    EXPLANATION = 'EXPLANATION',
    IMPORT = 'IMPORT',
}

@Component({
    selector: 'jhi-tutorial-registrations-import-modal',
    imports: [DialogModule, TranslateDirective, ButtonDirective],
    templateUrl: './tutorial-registrations-import-modal.component.html',
    styleUrl: './tutorial-registrations-import-modal.component.scss',
})
export class TutorialRegistrationsImportModalComponent {
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);

    isOpen = signal(false);
    flowStep = signal<ImportFlowStep>(ImportFlowStep.EXPLANATION);
    header = computed<string>(() => this.computeHeader());
    parsedStudents = signal<StudentDTO[]>([]);
    loading = signal(false);

    open() {
        this.isOpen.set(true);
    }

    protected readonly ImportFlowStep = ImportFlowStep;

    computeHeader(): string {
        this.currentLocale();
        const headerKey: string =
            this.flowStep() === ImportFlowStep.EXPLANATION
                ? 'artemisApp.pages.tutorialGroupRegistrations.importModal.explanationHeader'
                : 'artemisApp.pages.tutorialGroupRegistrations.importModal.confirmImportHeader';
        return this.translateService.instant(headerKey);
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

        this.parsedStudents.set(parsedStudents);
        this.loading.set(false);
        this.flowStep.set(ImportFlowStep.IMPORT);
    }
}

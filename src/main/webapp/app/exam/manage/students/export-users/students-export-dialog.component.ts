import { Component, InputSignal, ModelSignal, ViewEncapsulation, WritableSignal, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faBan, faFileExport } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExportExamUserDTO } from 'app/exam/manage/students/export-users/students-export.model';
import { TranslateService } from '@ngx-translate/core';
import Papa from 'papaparse';

@Component({
    selector: 'jhi-students-export-dialog',
    standalone: true,
    templateUrl: './students-export-dialog.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, FaIconComponent, DialogModule, ButtonModule],
})
export class StudentsExportDialogComponent {
    protected readonly faBan = faBan;
    protected readonly faFileExport = faFileExport;

    protected dialogVisible: ModelSignal<boolean> = model(false);
    protected lastExportAttemptFailed: WritableSignal<boolean> = signal(false);

    private readonly translateService: TranslateService = inject(TranslateService);
    private readonly examManagementService: ExamManagementService = inject(ExamManagementService);

    courseId: InputSignal<number> = input.required();
    exam: InputSignal<Exam> = input.required();

    openDialog(): void {
        this.dialogVisible.set(true);
    }

    closeDialog(): void {
        this.lastExportAttemptFailed.set(false);
        this.dialogVisible.set(false);
    }

    private downloadBlob(blob: Blob, filename: string): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
        URL.revokeObjectURL(url);
    }

    exportUsers(): void {
        this.examManagementService.exportExamUsers(this.courseId(), this.exam().id!).subscribe({
            next: (exportExamUsers: ExportExamUserDTO[]) => {
                const csvBlob: Blob = this.createCsv(exportExamUsers);
                this.downloadBlob(csvBlob, `exam-${this.exam().id}-students.csv`);
                this.closeDialog();
            },
            error: (_error) => {
                this.lastExportAttemptFailed.set(true);
            },
        });
    }

    private sanitizeCsvValue(value?: string): string | undefined {
        return value && /^[=+\-@]/.test(value) ? `'${value}` : value;
    }

    private createCsv(data: ExportExamUserDTO[]): Blob {
        const header: string[] = [
            this.translateService.instant('artemisApp.exam.examUsers.export.header.matriculationNumber'),
            this.translateService.instant('artemisApp.exam.examUsers.export.header.login'),
            this.translateService.instant('artemisApp.exam.examUsers.export.header.name'),
            this.translateService.instant('artemisApp.exam.examUsers.export.header.email'),
            this.translateService.instant('artemisApp.exam.examUsers.export.header.room'),
            this.translateService.instant('artemisApp.exam.examUsers.export.header.seat'),
            this.translateService.instant('artemisApp.exam.examUsers.export.header.fullLocation'),
        ];

        const rows = data.map((examUser) => [
            this.sanitizeCsvValue(examUser.matriculationNumber),
            this.sanitizeCsvValue(examUser.login),
            this.sanitizeCsvValue(examUser.name),
            this.sanitizeCsvValue(examUser.email),
            this.sanitizeCsvValue(examUser.room),
            this.sanitizeCsvValue(examUser.seat),
            this.sanitizeCsvValue(examUser.room ? `${this.exam().title}; ${examUser.fullLocation ?? ''}: ${examUser.seat ?? ''}` : undefined),
        ]);

        const csv: string = Papa.unparse([header, ...rows], {
            quotes: true,
            delimiter: ',',
        });

        return new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    }
}

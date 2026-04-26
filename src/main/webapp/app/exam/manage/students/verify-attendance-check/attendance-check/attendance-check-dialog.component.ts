import { Component, ViewEncapsulation, inject, input, output, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamUserAttendanceCheckDTO } from 'app/exam/shared/entities/exam-users-attendance-check-dto.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import dayjs from 'dayjs/esm';
import { DialogModule } from 'primeng/dialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-attendance-check-dialog',
    standalone: true,
    templateUrl: './attendance-check-dialog.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [DialogModule, TranslateDirective, HelpIconComponent, FaIconComponent],
})
export class AttendanceCheckDialogComponent {
    readonly dialogVisible = signal(false);
    private readonly alertService = inject(AlertService);
    private readonly examManagementService = inject(ExamManagementService);

    readonly courseId = input.required<number>();
    readonly exam = input.required<Exam>();
    readonly save = output<ExamUserAttendanceCheckDTO>();

    readonly MISSING_IMAGE = '/content/images/missing_image.png';
    readonly faBan = faBan;
    readonly faSave = faSave;

    examUserAttendanceCheck!: ExamUserAttendanceCheckDTO;

    openDialog(examUserAttendanceCheck: ExamUserAttendanceCheckDTO): void {
        this.examUserAttendanceCheck = { ...examUserAttendanceCheck };
        this.dialogVisible.set(true);
    }

    closeDialog(): void {
        this.dialogVisible.set(false);
    }

    onSaveSuccess(): void {
        this.save.emit(this.examUserAttendanceCheck);
        this.closeDialog();
    }

    onSaveError(): void {
        this.alertService.error('artemisApp.exam.examUsers.attendanceCheckSaveError');
    }

    saveChanges(): void {
        this.examManagementService.updateExamUser(this.courseId(), this.exam().id!, this.examUserAttendanceCheck).subscribe({
            next: (httpResponse) => {
                this.examUserAttendanceCheck = { ...this.examUserAttendanceCheck, ...httpResponse.body };
                this.onSaveSuccess();
            },
            error: () => this.onSaveError(),
        });
    }

    protected isBeforeExamEndPlusTwoHours(): boolean {
        const endDate = this.exam().endDate;

        if (!endDate) {
            return false;
        }

        return dayjs().isBefore(endDate.add(2, 'hour'));
    }

    protected getExamUserFullName(): string {
        const name: string = this.examUserAttendanceCheck.firstName + ' ' + this.examUserAttendanceCheck.lastName;
        return name.trim();
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}

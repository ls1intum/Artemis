import { Component, inject } from '@angular/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export type CourseExamArchiveDialogMode = 'Exam' | 'Course';
export type CourseExamArchiveDialogResult = 'archive-confirm' | 'archive' | undefined;

export interface CourseExamArchiveDialogData {
    archiveMode: CourseExamArchiveDialogMode;
    courseTitle?: string;
    examTitle?: string;
    warnings?: string[];
}

abstract class CourseExamArchiveDialogBase {
    protected readonly dialogRef = inject<TumAetUiDialogRef<CourseExamArchiveDialogResult>>(TumAetUiDialogRef);
    protected readonly dialogConfig = inject<TumAetUiDialogConfig<CourseExamArchiveDialogData>>(TumAetUiDialogConfig);

    readonly data = this.dialogConfig.data as CourseExamArchiveDialogData;

    close(result?: CourseExamArchiveDialogResult): void {
        this.dialogRef.close(result);
    }
}

/** The dialog header (title and close button) is rendered by the dialog shell, so the content starts with the body. */
@Component({
    selector: 'jhi-course-exam-archive-warning-dialog',
    imports: [TranslateDirective, TumAetUiButtonDirective],
    template: `
        <div>
            @if (data.archiveMode === 'Course') {
                <p [jhiTranslate]="'artemisApp.courseExamArchive.popup.course.question'" [translateValues]="{ title: data.courseTitle || '' }">
                    Are you sure you want to archive??
                </p>
                <p [jhiTranslate]="'artemisApp.courseExamArchive.popup.course.statement1'">
                    The process will compress all student code repositories, file upload exercises, modeling exercises, and text exercises for exercises and exams.
                </p>
                <p [jhiTranslate]="'artemisApp.courseExamArchive.popup.course.statement1b'">Additionally, the archive will include comprehensive student data.</p>
            }
            @if (data.archiveMode === 'Exam') {
                <p [jhiTranslate]="'artemisApp.courseExamArchive.popup.exam.question'" [translateValues]="{ title: data.examTitle || '' }">Are you sure you want to archive??</p>
                <p [jhiTranslate]="'artemisApp.courseExamArchive.popup.exam.statement1'">
                    The process will compress all student code repositories, file upload exercises, modeling exercises, and text exercises in the exam.
                </p>
            }
            <p [jhiTranslate]="'artemisApp.courseExamArchive.popup.statement2'">
                This process can take several hours depending on the number of students and programming exercises and will take up many server resources. Please start this process
                only once when the server load is low (e.g. early in the morning)
            </p>
            <p [jhiTranslate]="'artemisApp.courseExamArchive.popup.footerStatement'">
                You will receive a notification when the process is finished. Then you can download the archive as zip file on this page.
            </p>
        </div>
        <div class="mt-4! flex justify-end gap-2">
            <button
                tumAetUiButton
                type="button"
                severity="warn"
                data-testid="archive-confirm-button"
                (click)="close('archive-confirm')"
                [jhiTranslate]="data.archiveMode === 'Course' ? 'artemisApp.courseExamArchive.archiveCourse' : 'artemisApp.courseExamArchive.archiveExam'"
            >
                Archive
            </button>
        </div>
    `,
})
export class CourseExamArchiveWarningDialogComponent extends CourseExamArchiveDialogBase {}

@Component({
    selector: 'jhi-course-exam-archive-overwrite-dialog',
    imports: [TranslateDirective, TumAetUiButtonDirective],
    template: `
        <div>
            <p [jhiTranslate]="'artemisApp.courseExamArchive.confirmArchive.message'">
                Warning! The course has already been archived. If you continue, the archive will be overwritten! Are you sure you want to continue?
            </p>
        </div>
        <div class="mt-4! flex justify-end gap-2">
            <button tumAetUiButton type="button" severity="warn" data-testid="archive-overwrite-confirm-button" (click)="close('archive')" [jhiTranslate]="'global.generic.yes'">
                Yes
            </button>
            <button tumAetUiButton type="button" severity="success" data-testid="archive-overwrite-cancel-button" [jhiTranslate]="'global.generic.no'" (click)="close()">No</button>
        </div>
    `,
})
export class CourseExamArchiveOverwriteDialogComponent extends CourseExamArchiveDialogBase {}

@Component({
    selector: 'jhi-course-exam-archive-completed-with-warnings-dialog',
    imports: [TranslateDirective, TumAetUiButtonDirective],
    template: `
        <div>
            <p [jhiTranslate]="'artemisApp.courseExamArchive.archiveSuccessWithWarnings'">The archival process has completed with the following warnings:</p>
            <ul class="h-[200px] overflow-auto" data-testid="archive-warnings-list">
                @for (item of data.warnings ?? []; track item) {
                    <li>{{ item }}</li>
                }
            </ul>
        </div>
        <div class="mt-4! flex justify-end gap-2">
            <button tumAetUiButton type="button" severity="secondary" data-testid="archive-warnings-close-button" jhiTranslate="entity.action.close" (click)="close()">
                Close
            </button>
        </div>
    `,
})
export class CourseExamArchiveCompletedWithWarningsDialogComponent extends CourseExamArchiveDialogBase {}

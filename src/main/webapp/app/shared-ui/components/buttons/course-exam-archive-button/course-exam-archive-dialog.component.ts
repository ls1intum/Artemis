import { Component, inject } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
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
    protected readonly dialogRef = inject(DynamicDialogRef);
    protected readonly dialogConfig = inject(DynamicDialogConfig);

    readonly data = this.dialogConfig.data as CourseExamArchiveDialogData;

    close(result?: CourseExamArchiveDialogResult): void {
        this.dialogRef.close(result);
    }
}

@Component({
    selector: 'jhi-course-exam-archive-warning-dialog',
    imports: [TranslateDirective],
    template: `
        <div class="modal-header">
            <h4 class="modal-title">
                @if (data.archiveMode === 'Course') {
                    <span [jhiTranslate]="'artemisApp.courseExamArchive.popup.course.title'">Confirm Archive Course Operation</span>
                }
                @if (data.archiveMode === 'Exam') {
                    <span [jhiTranslate]="'artemisApp.courseExamArchive.popup.exam.title'">Confirm Archive Exam Operation</span>
                }
            </h4>
            <button type="button" class="btn-close" aria-label="Close" (click)="close()"></button>
        </div>
        <div class="modal-body">
            @if (data.archiveMode === 'Course') {
                <p [jhiTranslate]="'artemisApp.courseExamArchive.popup.course.question'" [translateValues]="{ title: data.courseTitle }">Are you sure you want to archive??</p>
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
        <div class="modal-footer">
            <button
                type="button"
                class="btn btn-warning"
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
    imports: [TranslateDirective],
    template: `
        <div class="modal-header">
            <h4 class="modal-title">
                <span [jhiTranslate]="'artemisApp.courseExamArchive.confirmArchive.title'">Warning: an archive already exists!</span>
            </h4>
        </div>
        <div class="modal-body">
            <p [jhiTranslate]="'artemisApp.courseExamArchive.confirmArchive.message'">
                Warning! The course has already been archived. If you continue, the archive will be overwritten! Are you sure you want to continue?
            </p>
        </div>
        <div class="modal-footer">
            <button type="button" class="btn btn-warning" (click)="close('archive')" [jhiTranslate]="'global.generic.yes'">Yes</button>
            <button type="button" class="btn btn-success" [jhiTranslate]="'global.generic.no'" (click)="close()">No</button>
        </div>
    `,
})
export class CourseExamArchiveOverwriteDialogComponent extends CourseExamArchiveDialogBase {}

@Component({
    selector: 'jhi-course-exam-archive-completed-with-warnings-dialog',
    imports: [TranslateDirective],
    template: `
        <div class="modal-header">
            <h4 class="modal-title">
                @if (data.archiveMode === 'Course') {
                    <span [jhiTranslate]="'artemisApp.courseExamArchive.archiveCourseSuccess'">The course has been archived!</span>
                }
                @if (data.archiveMode === 'Exam') {
                    <span [jhiTranslate]="'artemisApp.courseExamArchive.archiveExamSuccess'">The exam has been archived!</span>
                }
            </h4>
            <button type="button" class="btn-close" aria-label="Close" (click)="close()"></button>
        </div>
        <div class="modal-body">
            <p [jhiTranslate]="'artemisApp.courseExamArchive.archiveSuccessWithWarnings'">The archival process has completed with the following warnings:</p>
            <br />
            <ul style="height: 200px; overflow: auto">
                @for (item of data.warnings ?? []; track item) {
                    <li>{{ item }}</li>
                }
            </ul>
        </div>
        <div class="modal-footer">
            <button type="button" class="btn btn-warning" (click)="close()">Close</button>
        </div>
    `,
})
export class CourseExamArchiveCompletedWithWarningsDialogComponent extends CourseExamArchiveDialogBase {}

import { Component, OnInit, computed, inject, input, model, signal } from '@angular/core';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamExerciseHeaderComponent } from 'app/exam/overview/exercises/exam-exercise-header/exam-exercise-header.component';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamExerciseUpdateHighlighterComponent } from '../exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { UpperCasePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { TumAetUiButtonDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';
import { FileService } from 'app/foundation/service/file.service';
import { faCloudArrowUp, faFile, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { finalize } from 'rxjs/operators';

@Component({
    selector: 'jhi-file-upload-submission-exam',
    templateUrl: './file-upload-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: FileUploadExamSubmissionComponent }],
    imports: [
        TranslateDirective,
        ExamExerciseHeaderComponent,
        ResizeableContainerComponent,
        FaIconComponent,
        ExamExerciseUpdateHighlighterComponent,
        UpperCasePipe,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiTagComponent,
    ],
})
export class FileUploadExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    private fileUploadSubmissionService = inject(FileUploadSubmissionService);
    private examParticipationService = inject(ExamParticipationService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private fileService = inject(FileService);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    exerciseType = ExerciseType.FILE_UPLOAD;

    studentSubmission = model.required<FileUploadSubmission>();
    exercise = input.required<FileUploadExercise>();
    readonly problemStatementHtml = signal<SafeHtml | undefined>(undefined);

    readonly uploading = signal(false);
    readonly uploadFailed = signal(false);
    readonly isImage = computed(() => ['png', 'jpg', 'jpeg', 'gif', 'webp', 'avif'].includes(this.submittedFileExtension()?.toLowerCase() ?? ''));
    readonly acceptedFileTypes = computed(() =>
        this.exercise()
            .filePattern?.split(',')
            .map((extension) => '.' + extension.trim().replace(/^\./, ''))
            .join(','),
    );

    readonly submittedFileName = signal<string | undefined>(undefined);
    readonly submittedFileExtension = signal<string | undefined>(undefined);
    readonly submittedFileUrl = signal<string | undefined>(undefined);
    /** Bumped after every successful upload, because a replacement with the same filename keeps the same submission URL. */
    private readonly previewRevision = signal(0);
    readonly previewUrl = computed(() => {
        const url = this.submittedFileUrl();
        const revision = this.previewRevision();
        if (!url || revision <= 0) {
            return url;
        }
        const separator = url.includes('?') ? '&' : '?';
        return `${url}${separator}rev=${revision}`;
    });
    participation?: StudentParticipation;
    result?: Result;
    submissionFile?: File;

    // Icons
    farListAlt = faListAlt;
    readonly faCloudArrowUp = faCloudArrowUp;
    readonly faFile = faFile;
    readonly faSpinner = faSpinner;

    /**
     * Initializes data for file upload editor
     */
    ngOnInit() {
        // show submission answers in UI
        this.problemStatementHtml.set(this.artemisMarkdown.safeHtmlForMarkdown(this.exercise()?.problemStatement));
        this.updateViewFromSubmission();
    }

    /**
     * Updates the problem statement html of the currently loaded file upload exercise which is part of the user's student exam.
     * @param newProblemStatementHtml is the updated problem statement html that should be displayed to the user.
     */
    updateProblemStatement(newProblemStatementHtml: SafeHtml): void {
        this.problemStatementHtml.set(newProblemStatementHtml);
    }

    /**
     * Sets file submission for exercise
     * Here the file selected with the -browse- button is handled.
     * @param event {object} Event object which contains the uploaded file
     */
    setFileSubmissionForExercise(event: Event): void {
        const input = event.target as HTMLInputElement;
        this.selectFile(input.files?.[0]);
        // Allow selecting the same file again after a failed upload.
        input.value = '';
    }

    onDrop(event: DragEvent): void {
        event.preventDefault();
        this.selectFile(event.dataTransfer?.files[0]);
    }

    private selectFile(submissionFile?: File): void {
        if (!submissionFile || this.uploading() || this.readonly() || this.examTimeline() || !this.isActive || this.result) {
            return;
        }
        const allowedFileExtensions =
            this.exercise()
                .filePattern?.split(',')
                .map((extension) => extension.trim().replace(/^\./, '').toLowerCase()) ?? [];
        const extension = submissionFile.name.split('.').pop()?.toLowerCase();
        if (!extension || !allowedFileExtensions.includes(extension)) {
            this.alertService.error('artemisApp.fileUploadSubmission.fileExtensionError');
        } else if (submissionFile.size > MAX_SUBMISSION_FILE_SIZE) {
            this.alertService.error('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: submissionFile.name });
        } else {
            this.submissionFile = submissionFile;
            this.studentSubmission().isSynced = false;
            this.examParticipationService.notifySubmissionSyncStateChanged();
            this.saveUploadedFile();
        }
    }

    downloadFile(filePath: string) {
        this.fileService.downloadFile(filePath);
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.exercise() && (!this.exercise().dueDate || dayjs(this.exercise().dueDate).isSameOrAfter(dayjs()));
    }

    getExerciseId(): number | undefined {
        return this.exercise().id;
    }
    getExercise(): Exercise {
        return this.exercise();
    }

    public hasUnsavedChanges(): boolean {
        return !this.studentSubmission().isSynced!;
    }

    getSubmission(): Submission {
        return this.studentSubmission();
    }

    updateSubmissionFromView(): void {
        // we do nothing here as the new file path comes from the server
    }

    /**
     *  Here the new filePath, which was received from the server, is used to display the name and type of the just uploaded file.
     */
    updateViewFromSubmission(): void {
        if ((this.studentSubmission().isSynced && this.studentSubmission().filePath) || (this.examTimeline() && this.studentSubmission().filePath)) {
            // clear submitted file so that it is not displayed in the input (this might be confusing)
            this.submissionFile = undefined;
            // submissions loaded from the server (e.g. in the exam timeline) only contain the file path, the download url is derived from it
            this.submittedFileUrl.set(this.studentSubmission().filePathUrl ?? addPublicFilePrefix(this.studentSubmission().filePath));
            const filePath = this.studentSubmission().filePath!.split('/');
            const submittedFileName = filePath.last()!;
            this.submittedFileName.set(submittedFileName);
            const fileName = submittedFileName.split('.');
            this.submittedFileExtension.set(fileName.last());
        }
    }

    /**
     *  Here we send the submissionFile obtained in setFileSubmissionForExercise() to the server with the update method. The server returns the path to the file, and we
     *  set it in the submission.
     */
    saveUploadedFile() {
        if (!this.submissionFile || this.uploading() || this.readonly() || this.examTimeline() || !this.isActive || this.result) {
            return;
        }
        const submission = this.studentSubmission();
        this.uploadFailed.set(false);
        this.uploading.set(true);
        this.examParticipationService.setSubmissionSaving(submission, true);
        this.fileUploadSubmissionService
            .update(submission, this.exercise().id!, this.submissionFile)
            .pipe(
                finalize(() => {
                    this.uploading.set(false);
                    this.examParticipationService.setSubmissionSaving(submission, false);
                }),
            )
            .subscribe({
                next: (submissionFromServer) => {
                    submission.filePath = submissionFromServer.filePath;
                    submission.filePathUrl = addPublicFilePrefix(submissionFromServer.filePath);
                    submission.isSynced = true;
                    submission.submitted = true;
                    this.examParticipationService.notifySubmissionSyncStateChanged();
                    this.submissionFile = undefined;
                    this.previewRevision.update((revision) => revision + 1);
                    if (this.studentSubmission() === submission) {
                        this.updateViewFromSubmission();
                    }
                },
                error: () => this.onError(),
            });
    }

    /**
     * Pass on an error to the browser console and the alertService.
     */
    private onError() {
        this.uploadFailed.set(true);
        this.alertService.error(this.translateService.instant('error.fileUploadSavingError'));
    }

    setSubmissionVersion(submissionVersion: SubmissionVersion): void {
        // if we do not assign the parameter, eslint will complain because either the parameter is unused or if we suppress this with ts-ignore that ts-ignore shadows compilation errors.
        this.submissionVersion = submissionVersion;
        // submission versions are not supported for file upload exercises
        throw new Error('Submission versions are not supported for file upload exercises.');
    }
}

import { Component, ElementRef, OnInit, inject, input, model, viewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { ButtonType } from 'app/shared/components/button.component';
import { Result } from 'app/entities/result.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamExerciseUpdateHighlighterComponent } from '../exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { UpperCasePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { FileService } from 'app/shared/service/file.service';

@Component({
    selector: 'jhi-file-upload-submission-exam',
    templateUrl: './file-upload-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: FileUploadExamSubmissionComponent }],
    imports: [
        TranslateDirective,
        IncludedInScoreBadgeComponent,
        ResizeableContainerComponent,
        FaIconComponent,
        ExamExerciseUpdateHighlighterComponent,
        UpperCasePipe,
        ArtemisTranslatePipe,
    ],
})
export class FileUploadExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    private fileUploadSubmissionService = inject(FileUploadSubmissionService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private fileService = inject(FileService);

    exerciseType = ExerciseType.FILE_UPLOAD;

    fileInput = viewChild<ElementRef>('fileInput');

    studentSubmission = model.required<FileUploadSubmission>();
    exercise = input.required<FileUploadExercise>();
    problemStatementHtml: string;

    submittedFileName: string;
    submittedFileExtension: string;
    participation: StudentParticipation;
    result: Result;
    submissionFile?: File;

    readonly ButtonType = ButtonType;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    filePath?: string;

    // Icons
    farListAlt = faListAlt;

    /**
     * Initializes data for file upload editor
     */
    ngOnInit() {
        // show submission answers in UI
        this.problemStatementHtml = htmlForMarkdown(this.exercise()?.problemStatement);
        this.updateViewFromSubmission();
    }

    /**
     * Updates the problem statement html of the currently loaded file upload exercise which is part of the user's student exam.
     * @param newProblemStatementHtml is the updated problem statement html that should be displayed to the user.
     */
    updateProblemStatement(newProblemStatementHtml: string): void {
        this.problemStatementHtml = newProblemStatementHtml;
        this.changeDetectorReference.detectChanges();
    }

    /**
     * Sets file submission for exercise
     * Here the file selected with the -browse- button is handled.
     * @param event {object} Event object which contains the uploaded file
     */
    setFileSubmissionForExercise(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            const submissionFile = fileList[0];
            const allowedFileExtensions = this.exercise().filePattern!.split(',');
            if (!allowedFileExtensions.some((extension) => submissionFile.name.toLowerCase().endsWith(extension))) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileExtensionError');
            } else if (submissionFile.size > MAX_SUBMISSION_FILE_SIZE) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: submissionFile.name });
            } else {
                this.submissionFile = submissionFile;
                this.studentSubmission().isSynced = false;
            }
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
            const filePath = this.studentSubmission()!.filePath!.split('/');
            this.submittedFileName = filePath.last()!;
            const fileName = this.submittedFileName.split('.');
            this.submittedFileExtension = fileName.last()!;
        }
    }

    /**
     *  Here we send the submissionFile obtained in setFileSubmissionForExercise() to the server with the update method. The server returns the path to the file, and we
     *  set it in the submission.
     */
    saveUploadedFile() {
        if (!this.submissionFile) {
            return;
        }
        this.fileUploadSubmissionService.update(this.studentSubmission() as FileUploadSubmission, this.exercise().id!, this.submissionFile).subscribe({
            next: (res) => {
                const submissionFromServer = res.body!;
                this.studentSubmission().filePath = submissionFromServer.filePath;
                this.studentSubmission().filePathUrl = addPublicFilePrefix(submissionFromServer.filePath);
                this.studentSubmission().isSynced = true;
                this.studentSubmission().submitted = true;
                this.updateViewFromSubmission();
            },
            error: () => this.onError(),
        });
    }

    /**
     * Pass on an error to the browser console and the alertService.
     */
    private onError() {
        this.alertService.error(this.translateService.instant('error.fileUploadSavingError'));
    }

    setSubmissionVersion(submissionVersion: SubmissionVersion): void {
        // if we do not assign the parameter, eslint will complain because either the parameter is unused or if we suppress this with ts-ignore that ts-ignore shadows compilation errors.
        this.submissionVersion = submissionVersion;
        // submission versions are not supported for file upload exercises
        throw new Error('Submission versions are not supported for file upload exercises.');
    }
}

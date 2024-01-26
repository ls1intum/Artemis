import { Component, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Complaint } from 'app/entities/complaint.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { map, switchMap, tap } from 'rxjs/operators';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { DiffMatchPatch } from 'diff-match-patch-typescript';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';

@Component({
    selector: 'jhi-repository-view',
    templateUrl: './repository-view.component.html',
    styleUrl: './repository-view.component.scss',
})
export class RepositoryViewComponent implements OnInit, OnDestroy {
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    readonly diffMatchPatch = new DiffMatchPatch();
    readonly getCourseFromExercise = getCourseFromExercise;

    paramSub: Subscription;
    participation: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;
    submission?: ProgrammingSubmission;
    manualResult?: Result;
    userId: number;
    // for assessment-layout
    isTestRun = false;
    saveBusy = false;
    submitBusy = false;
    cancelBusy = false;
    nextSubmissionBusy = false;
    isAssessor = false;
    assessmentsAreValid = false;
    complaint: Complaint;
    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    loadingParticipation = false;
    participationCouldNotBeFetched = false;
    showEditorInstructions = true;
    hasAssessmentDueDatePassed: boolean;
    examId = 0;
    exerciseId: number;
    exerciseGroupId: number;
    exerciseDashboardLink: string[];
    loadingInitialSubmission = true;
    highlightDifferences = false;

    localVCEnabled = false;

    lockLimitReached = false;

    templateParticipation: TemplateProgrammingExerciseParticipation;
    templateFileSession: { [fileName: string]: string } = {};

    // function override, if set will be executed instead of going to the next submission page
    @Input() overrideNextSubmission?: (submissionId: number) => any = undefined;

    constructor(
        private accountService: AccountService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private domainService: DomainService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private repositoryFileService: CodeEditorRepositoryFileService,
        private programmingExerciseService: ProgrammingExerciseService,
        private profileService: ProfileService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation id with the latest result and result details.
     */
    ngOnInit(): void {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.paramSub = this.route.params.subscribe((params) => {
            this.loadingParticipation = true;
            this.participationCouldNotBeFetched = false;

            const participationId = Number(params['participationId']);
            this.loadParticipationWithLatestResult(participationId)
                .pipe(
                    tap((participationWithResults) => {
                        this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResults]);
                        this.participation = participationWithResults;
                        this.exercise = this.participation.exercise as ProgrammingExercise;
                    }),
                    // The following is needed for highlighting changed code lines
                    switchMap(() => this.programmingExerciseService.findWithTemplateAndSolutionParticipation(this.exercise.id!)),
                    tap((programmingExercise) => (this.templateParticipation = programmingExercise.body!.templateParticipation!)),
                    switchMap(() => {
                        // Get all files with content from template repository
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.templateParticipation]);
                        const observable = this.repositoryFileService.getFilesWithContent();
                        // Set back to student participation
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.participation]);
                        return observable;
                    }),
                    tap((templateFilesObj) => {
                        if (templateFilesObj) {
                            this.templateFileSession = templateFilesObj;
                        }
                    }),
                )
                .subscribe({
                    next: () => {
                        this.loadingParticipation = false;
                    },
                    error: () => {
                        this.participationCouldNotBeFetched = true;
                        this.loadingParticipation = false;
                    },
                });
        });

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });
    }

    /**
     * Load the participation from server with the latest result.
     * @param participationId
     */
    loadParticipationWithLatestResult(participationId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.programmingExerciseParticipationService.getStudentParticipationWithLatestResult(participationId).pipe(
            map((participation: ProgrammingExerciseStudentParticipation) => {
                if (participation.results?.length) {
                    // connect result and participation
                    participation.results[0].participation = participation;
                }
                return participation;
            }),
        );
    }

    /**
     * If a subscription exists for paramSub, unsubscribe
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Triggers when a new file was selected in the code editor. Compares the content of the file with the template (if available), calculates the diff
     * and highlights the changed/added lines or all lines if the file is not in the template.
     *
     * @param selectedFile name of the file which is currently displayed
     */
    onFileLoad(selectedFile: string): void {
        if (selectedFile && this.codeEditorContainer?.selectedFile) {
            // When the selectedFile is not part of the template, then this is a new file and all lines in code editor are highlighted
            if (!this.templateFileSession[selectedFile]) {
                const lastLine = this.codeEditorContainer.aceEditor.editorSession.getLength() - 1;
                this.highlightLines(0, lastLine);
            } else {
                // Calculation of the diff, see: https://github.com/google/diff-match-patch/wiki/Line-or-Word-Diffs
                const diffArray = this.diffMatchPatch.diff_linesToChars(this.templateFileSession[selectedFile], this.codeEditorContainer.aceEditor.editorSession.getValue());
                const lineText1 = diffArray.chars1;
                const lineText2 = diffArray.chars2;
                const lineArray = diffArray.lineArray;
                const diffs = this.diffMatchPatch.diff_main(lineText1, lineText2, false);
                this.diffMatchPatch.diff_charsToLines(diffs, lineArray);

                // Setup counter to know on which range to highlight in the code editor
                let counter = 0;
                diffs.forEach((diffElement) => {
                    // No changes
                    if (diffElement[0] === 0) {
                        const lines = diffElement[1].split(/\r?\n/);
                        counter += lines.length - 1;
                    }
                    // Newly added
                    if (diffElement[0] === 1) {
                        const lines = diffElement[1].split(/\r?\n/).filter(Boolean);
                        const firstLineToHighlight = counter;
                        const lastLineToHighlight = counter + lines.length - 1;
                        this.highlightLines(firstLineToHighlight, lastLineToHighlight);
                        counter += lines.length;
                    }
                });
            }
        }
    }

    private highlightLines(firstLine: number, lastLine: number) {
        this.codeEditorContainer.aceEditor.highlightLines(firstLine, lastLine, 'diff-newLine', 'gutter-diff-newLine');
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.alertService.error(error);
        this.saveBusy = this.cancelBusy = this.submitBusy = this.nextSubmissionBusy = false;
    }
}

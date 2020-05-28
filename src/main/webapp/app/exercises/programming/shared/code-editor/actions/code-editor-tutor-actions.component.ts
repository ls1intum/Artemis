import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorSubmissionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-submission.service';
import { AssessmentActionState, CommitState, EditorState, GitConflictState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { Observable, of, Subscription } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, tap } from 'rxjs/operators';
import { Result } from 'app/entities/result.model';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { Feedback } from 'app/entities/feedback.model';
import { HttpResponse } from '@angular/common/http';
import { Participation } from 'app/entities/participation/participation.model';

@Component({
    selector: 'jhi-code-editor-tutor-actions',
    templateUrl: './code-editor-tutor-actions.component.html',
    styleUrls: ['./code-editor-tutor-actions.component.scss'],
})
export class CodeEditorTutorActionsComponent implements OnInit, OnDestroy {
    CommitState = CommitState;
    EditorState = EditorState;
    FeatureToggle = FeatureToggle;

    @Input()
    buildable = true;
    @Input()
    unsavedFiles: { [fileName: string]: string };
    @Input() disableActions = false;

    @Input()
    get editorState() {
        return this.editorStateValue;
    }

    @Input()
    get commitState() {
        return this.commitStateValue;
    }
    @Input()
    result: Result;

    @Input()
    participation: Participation;

    @Input()
    feedbacks: Feedback[];

    @Output()
    commitStateChange = new EventEmitter<CommitState>();
    @Output()
    editorStateChange = new EventEmitter<EditorState>();
    @Output()
    isBuildingChange = new EventEmitter<boolean>();
    @Output()
    onSavedFiles = new EventEmitter<{ [fileName: string]: string | null }>();
    @Output()
    onError = new EventEmitter<string>();
    @Output()
    assessmentActionStateChange = new EventEmitter<AssessmentActionState>();

    isBuilding: boolean;
    editorStateValue: EditorState;
    commitStateValue: CommitState;
    assessmentActionStateValue: AssessmentActionState;

    conflictStateSubscription: Subscription;
    submissionSubscription: Subscription;

    set commitState(commitState: CommitState) {
        this.commitStateValue = commitState;
        this.commitStateChange.emit(commitState);
    }

    set editorState(editorState: EditorState) {
        this.editorStateValue = editorState;
        this.editorStateChange.emit(editorState);
    }

    set assessmentActionState(assessmentActionState: AssessmentActionState) {
        this.assessmentActionStateValue = assessmentActionState;
        this.assessmentActionStateChange.emit(assessmentActionState);
    }

    constructor(
        private repositoryService: CodeEditorRepositoryService,
        private repositoryFileService: CodeEditorRepositoryFileService,
        private conflictService: CodeEditorConflictStateService,
        private modalService: NgbModal,
        private submissionService: CodeEditorSubmissionService,
        private manualResultService: ProgrammingAssessmentManualResultService,
    ) {}

    ngOnInit(): void {
        this.conflictStateSubscription = this.conflictService.subscribeConflictState().subscribe((gitConflictState: GitConflictState) => {
            // When the conflict is encountered when opening the code-editor, setting the commitState here could cause an uncheckedException.
            // This is why a timeout of 0 is set to make sure the template is rendered before setting the commitState.
            if (this.commitState === CommitState.CONFLICT && gitConflictState === GitConflictState.OK) {
                // Case a: Conflict was resolved.
                setTimeout(() => (this.commitState = CommitState.UNDEFINED), 0);
            } else if (this.commitState !== CommitState.CONFLICT && gitConflictState === GitConflictState.CHECKOUT_CONFLICT) {
                // Case b: Conflict has occurred.
                setTimeout(() => (this.commitState = CommitState.CONFLICT), 0);
            }
        });
        this.submissionSubscription = this.submissionService
            .getBuildingState()
            .pipe(tap((isBuilding: boolean) => (this.isBuilding = isBuilding)))
            .subscribe();
    }

    ngOnDestroy(): void {
        if (this.conflictStateSubscription) {
            this.conflictStateSubscription.unsubscribe();
        }
    }

    onSave() {
        this.saveChangedFiles()
            .pipe(catchError(() => of()))
            .subscribe();
    }

    /**
     * @function saveFiles
     * @desc Saves all files that have unsaved changes in the editor.
     */
    saveChangedFiles(): Observable<any> {
        console.log('ich bin ein Save event');
        /*if (!_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.SAVING;
            const unsavedFiles = Object.entries(this.unsavedFiles).map(([fileName, fileContent]) => ({
                fileName,
                fileContent
            }));
            return this.repositoryFileService.updateFiles(unsavedFiles).pipe(
                tap((res) => this.onSavedFiles.emit(res)),
                catchError((err) => {
                    this.onError.emit(err.error);
                    this.editorState = EditorState.UNSAVED_CHANGES;
                    return throwError('saving failed');
                }),
            );
        } else {
            return Observable.of(null);
        }*/
        return Observable.of(null);
    }

    /**
     * @function commit
     * @desc Commits the current repository files.
     * If there are unsaved changes, save them first before trying to commit again.
     */
    commit() {
        console.log('Step1');
        this.assessmentActionState = AssessmentActionState.TO_SUBMIT;
        // Avoid multiple commits at the same time.
        /*   if (this.commitState === CommitState.COMMITTING) {
            return;
        }
        // If there are unsaved changes, save them before trying to commit again.
        Observable.of(null)
            .pipe(
                switchMap(() => (this.editorState === EditorState.UNSAVED_CHANGES ? this.saveChangedFiles() : Observable.of(null))),
                tap(() => (this.commitState = CommitState.COMMITTING)),
                switchMap(() => this.repositoryService.commit()),
                tap(() => {
                    this.commitState = CommitState.CLEAN;
                    // Note: this is not 100% clean, but not setting it here would complicate the state model.
                    // We just assume that after the commit a build happens if the repo is buildable.
                    if (this.buildable) {
                        this.isBuilding = true;
                    }
                }),
            )
            .subscribe(
                () => {
                },
                () => {
                    this.commitState = CommitState.UNCOMMITTED_CHANGES;
                    this.onError.emit('commitFailed');
                },
            );*/
        /*console.log("Participation");
        console.log(this.participation);
        console.log("Result");
        console.log(this.result);

        this.result.participation = this.participation;
        console.log(this.result.participation);
        this.participation.results[0]= this.result;
        //this.result.feedbacks = this.test();
        this.result.feedbacks = this.feedbacks;
        for (let i = 0; i < this.result.feedbacks.length; i++) {
            this.result.feedbacks[i].type = FeedbackType.MANUAL;
        }
        if (this.result.id != null) {
            this.subscribeToSaveResponse(this.manualResultService.update(this.participation.id, this.result));
        } else {
            // in case id is null or undefined
            this.subscribeToSaveResponse(this.manualResultService.create(this.participation.id, this.result));
        }*/
    }

    private test() {
        var testFeedback: Feedback[];
        var feedback: Feedback = new Feedback();
        feedback.text = 'Toll gemacht text';
        feedback.detailText = 'super detailText';
        feedback.credits = 0;
        //feedback.positive = true;
        //feedback.result = this.result;
        testFeedback = [feedback];
        return testFeedback;
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Result>>) {
        result.subscribe(
            (res) => this.onSaveSuccess(res),
            () => this.onSaveError(),
        );
    }

    /**
     * Closes the active model, sets iSaving to false and broadcasts the corresponding message on a successful save
     * @param {HttpResponse<Result>} result - The HTTP Response with the result
     */
    onSaveSuccess(result: HttpResponse<Result>) {
        console.log('onSaveSuccess');
        console.log(result.body);
    }

    /**
     * Only sets isSaving to false
     */
    onSaveError() {
        console.log('onSaveError');
    }
}

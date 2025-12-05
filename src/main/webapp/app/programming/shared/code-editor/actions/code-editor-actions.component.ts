import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    Input,
    NgZone,
    OnChanges,
    OnDestroy,
    OnInit,
    Output,
    SimpleChanges,
    inject,
    input,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { Observable, Subscription, of, throwError } from 'rxjs';
import { isEmpty as _isEmpty } from 'lodash-es';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { faCircleNotch, faExternalLink, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faPlayCircle } from '@fortawesome/free-regular-svg-icons';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { RequestFeedbackButtonComponent } from 'app/core/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getLocalRepositoryLink } from 'app/shared/util/navigation.utils';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService, ConnectionError } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { CodeEditorSubmissionService } from 'app/programming/shared/code-editor/services/code-editor-submission.service';
import { CommitState, EditorState, FileSubmission, GitConflictState } from '../model/code-editor.model';
import { CodeEditorConfirmRefreshModalComponent } from 'app/programming/shared/code-editor/actions/refresh-modal/code-editor-confirm-refresh-modal.component';
import { CodeEditorResolveConflictModalComponent } from 'app/programming/shared/code-editor/actions/conflict-modal/code-editor-resolve-conflict-modal.component';

@Component({
    selector: 'jhi-code-editor-actions',
    templateUrl: './code-editor-actions.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RequestFeedbackButtonComponent, FeatureToggleDirective, NgbTooltip, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, RouterLink],
})
export class CodeEditorActionsComponent implements OnInit, OnDestroy, OnChanges {
    private repositoryService = inject(CodeEditorRepositoryService);
    private repositoryFileService = inject(CodeEditorRepositoryFileService);
    private conflictService = inject(CodeEditorConflictStateService);
    private modalService = inject(NgbModal);
    private submissionService = inject(CodeEditorSubmissionService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private changeDetectorRef = inject(ChangeDetectorRef);
    private ngZone = inject(NgZone);

    CommitState = CommitState;
    EditorState = EditorState;
    FeatureToggle = FeatureToggle;

    @Input() buildable = true;
    @Input() unsavedFiles: { [fileName: string]: string };
    @Input() disableActions = false;
    @Input() disableAutoSave = false;
    @Input() get editorState() {
        return this.editorStateValue;
    }
    @Input() get commitState() {
        return this.commitStateValue;
    }
    participation = input<Participation>();

    @Output() commitStateChange = new EventEmitter<CommitState>();
    @Output() editorStateChange = new EventEmitter<EditorState>();
    @Output() isBuildingChange = new EventEmitter<boolean>();
    @Output() onSavedFiles = new EventEmitter<{ [fileName: string]: string | undefined }>();
    @Output() onRefreshFiles = new EventEmitter();
    @Output() onError = new EventEmitter<string>();

    private _isBuilding: boolean;
    editorStateValue: EditorState;
    commitStateValue: CommitState;
    isResolvingConflict = false;
    routerLink: string;
    repositoryLink: string[];
    isInCourseManagement = false;

    get isBuilding(): boolean {
        return this._isBuilding;
    }

    set isBuilding(value: boolean) {
        this._isBuilding = value;
        this.changeDetectorRef.markForCheck();
    }

    conflictStateSubscription: Subscription;
    submissionSubscription: Subscription;
    routeParamsSubscription: Subscription;

    // autoTimerInterval in seconds
    autoSaveTimer = 0;
    autoSaveInterval: number;

    // Icons
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faSync = faSync;
    farPlayCircle = faPlayCircle;
    faExternalLink = faExternalLink;

    set commitState(commitState: CommitState) {
        this.commitStateValue = commitState;
        this.commitStateChange.emit(commitState);
        this.changeDetectorRef.markForCheck();
    }

    set editorState(editorState: EditorState) {
        this.editorStateValue = editorState;
        this.editorStateChange.emit(editorState);
        this.changeDetectorRef.markForCheck();
    }

    ngOnInit(): void {
        this.routeParamsSubscription = this.route.params.subscribe((params) => {
            const repositoryType = params['repositoryType'] ?? 'USER';
            const courseId = Number(params['courseId']);
            const repositoryId = Number(params['repositoryId']);
            const exerciseId = Number(params['exerciseId']);
            const examId = Number(params['examId']);
            const exerciseGroupId = Number(params['exerciseGroupId']);
            this.repositoryLink = getLocalRepositoryLink(courseId, exerciseId, repositoryType, repositoryId, examId, exerciseGroupId);
            this.changeDetectorRef.markForCheck();
        });
        this.isInCourseManagement = this.router.url.includes('course-management');

        this.conflictStateSubscription = this.conflictService.subscribeConflictState().subscribe((gitConflictState: GitConflictState) => {
            // When the conflict is encountered when opening the code-editor, setting the commitState here could cause an uncheckedException.
            // Schedule the state change for the next tick to ensure template is rendered.
            if (this.commitState === CommitState.CONFLICT && gitConflictState === GitConflictState.OK) {
                // Case a: Conflict was resolved.
                setTimeout(() => {
                    this.commitState = CommitState.UNDEFINED;
                }, 0);
            } else if (this.commitState !== CommitState.CONFLICT && gitConflictState === GitConflictState.CHECKOUT_CONFLICT) {
                // Case b: Conflict has occurred.
                setTimeout(() => {
                    this.commitState = CommitState.CONFLICT;
                }, 0);
            }
        });
        this.submissionSubscription = this.submissionService
            .getBuildingState()
            .pipe(
                tap((isBuilding: boolean) => {
                    this.isBuilding = isBuilding;
                    // markForCheck is called in the setter
                }),
            )
            .subscribe();

        if (!this.disableAutoSave) {
            // Run interval outside Angular zone to prevent unnecessary change detection cycles
            this.ngZone.runOutsideAngular(() => {
                this.autoSaveInterval = window.setInterval(() => {
                    this.autoSaveTimer++;
                    if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                        this.autoSaveTimer = 0;
                        // Re-enter Angular zone only when we need to save
                        this.ngZone.run(() => this.onSave());
                    }
                }, AUTOSAVE_CHECK_INTERVAL);
            });
        }
    }

    /**
     * After save and commit, we need to wait for the 'save' to settle, see the setter {@link CodeEditorContainerComponent#unsavedFilesValue}.
     * This is because the user might have changed files while the commit was executing.
     * In that case, we do not reset the commit state to CommitState.CLEAN.
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        setTimeout(() => {
            if (changes.editorState && changes.editorState.previousValue === EditorState.SAVING && this.commitState === CommitState.COMMITTING) {
                if (changes.editorState.currentValue === EditorState.CLEAN) {
                    this.commitState = CommitState.CLEAN;
                } else {
                    this.commitState = CommitState.UNCOMMITTED_CHANGES;
                }
                // markForCheck is called in the setter
            }
        }, 0);
    }

    ngOnDestroy(): void {
        clearInterval(this.autoSaveInterval);
        this.onSave();

        if (this.conflictStateSubscription) {
            this.conflictStateSubscription.unsubscribe();
        }
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        if (this.routeParamsSubscription) {
            this.routeParamsSubscription.unsubscribe();
        }
    }

    onRefresh() {
        if (this.editorState !== EditorState.CLEAN) {
            const modal = this.modalService.open(CodeEditorConfirmRefreshModalComponent, { keyboard: true, size: 'lg' });
            modal.componentInstance.shouldRefresh.subscribe(() => {
                this.executeRefresh();
            });
        } else {
            this.executeRefresh();
        }
    }

    executeRefresh() {
        this.editorState = EditorState.REFRESHING;
        this.repositoryService.pull().subscribe({
            next: () => {
                this.onRefreshFiles.emit();
                this.editorState = EditorState.CLEAN;
            },
            error: (error: Error) => {
                this.editorState = EditorState.UNSAVED_CHANGES;
                if (error.message === ConnectionError.message) {
                    this.onError.emit('refreshFailed' + error.message);
                } else {
                    this.onError.emit('refreshFailed');
                }
            },
        });
    }

    onSave() {
        this.saveChangedFiles()
            .pipe(catchError(() => of()))
            .subscribe();
    }

    /**
     * @param andCommit whether the saved changed in the files should be committed or not
     * @desc Saves all files that have unsaved changes in the editor.
     */
    saveChangedFiles(andCommit = false): Observable<any> {
        if (!_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.SAVING;
            const unsavedFiles = Object.entries(this.unsavedFiles).map(([fileName, fileContent]) => ({ fileName, fileContent }));
            return this.repositoryFileService.updateFiles(unsavedFiles, andCommit).pipe(
                tap((fileSubmission: FileSubmission) => {
                    this.onSavedFiles.emit(fileSubmission);
                }),
                catchError((error: Error) => {
                    this.editorState = EditorState.UNSAVED_CHANGES;
                    if (error.message === ConnectionError.message) {
                        this.onError.emit('saveFailed' + error.message);
                    } else {
                        this.onError.emit('saveFailed');
                    }
                    return throwError(() => error);
                }),
            );
        }
        return of(undefined);
    }

    /**
     * @function commit
     * @desc Commits the current repository files.
     * If there are unsaved changes, save them first before trying to commit again.
     */
    commit() {
        // Avoid multiple commits at the same time.
        if (this.commitState === CommitState.COMMITTING) {
            return;
        }
        // If there are unsaved changes, save them before trying to commit again.
        of(undefined)
            .pipe(
                tap(() => (this.commitState = CommitState.COMMITTING)),
                switchMap(() => {
                    if (!_isEmpty(this.unsavedFiles)) {
                        return this.saveChangedFiles(true);
                    } else {
                        return this.repositoryService.commit();
                    }
                }),
                tap(() => {
                    if (this.editorState === EditorState.CLEAN) {
                        this.commitState = CommitState.CLEAN;
                    }
                    // We just assume that after the commit a build happens if the repo is buildable.
                    if (this.buildable) {
                        // Note: this is not 100% clean, but not setting it here would complicate the state model.
                        this.isBuilding = true;
                    }
                }),
            )
            .subscribe({
                error: (error: HttpErrorResponse) => {
                    this.commitState = CommitState.UNCOMMITTED_CHANGES;
                    if (error.message === ConnectionError.message) {
                        this.onError.emit('submitFailed' + error.message);
                    } else {
                        this.onError.emit('submitFailed');
                    }

                    if (error.error.detail) {
                        const detailMessage = error.error.detail;
                        if (detailMessage.includes('submitBeforeStartDate')) {
                            this.onError.emit('submitBeforeStartDate');
                        } else if (detailMessage.includes('submitAfterDueDate')) {
                            this.onError.emit('submitAfterDueDate');
                        } else if (detailMessage.includes('submitAfterReachingSubmissionLimit')) {
                            this.onError.emit('submitAfterReachingSubmissionLimit');
                        }
                    }
                },
            });
    }

    resetRepository() {
        const modal = this.modalService.open(CodeEditorResolveConflictModalComponent, { keyboard: true, size: 'lg' });
        modal.componentInstance.shouldReset.subscribe(() => {
            this.repositoryService.resetRepository().subscribe({
                next: () => {
                    this.conflictService.notifyConflictState(GitConflictState.OK);
                    this.executeRefresh();
                },
                error: () => {
                    this.onError.emit('resetFailed');
                },
            });
        });
    }
}

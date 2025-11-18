import { Component, ViewChild, inject, signal } from '@angular/core';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCircleNotch, faPlus, faSpinner, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { EditorState, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeGenerationRequestDTO } from 'app/openapi/model/codeGenerationRequestDTO';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { HttpClient } from '@angular/common/http';
import { Subscription, catchError, of, switchMap, take } from 'rxjs';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faCheckDouble } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
    styleUrl: 'code-editor-instructor-and-editor-container.scss',
    imports: [
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        NgbTooltip,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseInstructionComponent,
        NgbTooltip,
        ArtemisTranslatePipe,
    ],
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent {
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;
    @ViewChild(ProgrammingExerciseEditableInstructionComponent, { static: false }) editableInstructions: ProgrammingExerciseEditableInstructionComponent;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly consistencyIssues = signal<ConsistencyIssue[]>([]);

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private profileService = inject(ProfileService);

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;
    faSpinner = faSpinner;
    facArtemisIntelligence = facArtemisIntelligence;
    irisSettings?: IrisSettings;
    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faCheckDouble = faCheckDouble;
    private codeGenAlertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private hyperionWs = inject(HyperionWebsocketService);
    private repoService = inject(CodeEditorRepositoryService);
    private repoFileService = inject(CodeEditorRepositoryFileService);
    private http = inject(HttpClient);
    isGeneratingCode = signal(false);
    private jobSubscription?: Subscription;
    private jobTimeoutHandle?: number;

    /**
     * Starts Hyperion code generation after user confirmation.
     */
    generateCode(): void {
        if (!this.exercise?.id || this.isGeneratingCode()) {
            return;
        }

        if (this.selectedRepository !== RepositoryType.TEMPLATE && this.selectedRepository !== RepositoryType.SOLUTION && this.selectedRepository !== RepositoryType.TESTS) {
            this.codeGenAlertService.addAlert({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.unsupportedRepository' });
            return;
        }
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.codeGeneration.confirmTitle';
        modalRef.componentInstance.text = 'artemisApp.programmingExercise.codeGeneration.confirmText';
        modalRef.componentInstance.translateText = true;
        modalRef.result.then(() => this.startCodeGeneration()).catch(() => {});
    }

    /**
     * Triggers the async generation endpoint and subscribes to job updates.
     */
    private startCodeGeneration() {
        this.isGeneratingCode.set(true);
        const request: CodeGenerationRequestDTO = { repositoryType: this.selectedRepository as CodeGenerationRequestDTO.RepositoryTypeEnum };
        this.http.post<{ jobId: string }>(`api/hyperion/programming-exercises/${this.exercise!.id}/generate-code`, request).subscribe({
            next: (res) => {
                if (!res?.jobId) {
                    this.isGeneratingCode.set(false);
                    this.codeGenAlertService.addAlert({
                        type: AlertType.DANGER,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    });
                    return;
                }
                this.subscribeToJob(res.jobId);
            },
            error: (err) => {
                this.isGeneratingCode.set(false);
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                });
            },
            complete: () => {},
        });
    }

    /**
     * Subscribes to job updates, refreshes files on updates, and stops spinner on terminal events.
     * @param jobId job identifier
     */
    private subscribeToJob(jobId: string) {
        const cleanup = () => {
            this.isGeneratingCode.set(false);
            this.hyperionWs.unsubscribeFromJob(jobId);
            this.jobSubscription?.unsubscribe();
            if (this.jobTimeoutHandle) {
                clearTimeout(this.jobTimeoutHandle);
                this.jobTimeoutHandle = undefined;
            }
        };

        this.jobSubscription = this.hyperionWs.subscribeToJob(jobId).subscribe({
            next: (event) => {
                switch (event.type) {
                    case 'STARTED':
                        // spinner already on; just log
                        break;

                    case 'PROGRESS':
                        break;

                    case 'FILE_UPDATED':
                    case 'NEW_FILE':
                        this.repoService
                            .pull()
                            .pipe(
                                take(1),
                                catchError(() => {
                                    return of(void 0);
                                }),
                            )
                            .subscribe(() => {});
                        break;

                    case 'DONE':
                        if (this.codeEditorContainer) {
                            this.codeEditorContainer.editorState = EditorState.REFRESHING;
                        }
                        this.repoService
                            .pull()
                            .pipe(
                                take(1),
                                catchError(() => {
                                    return of(void 0);
                                }),
                                switchMap(() =>
                                    this.repoFileService.getRepositoryContent().pipe(
                                        take(1),
                                        catchError(() => {
                                            return of({});
                                        }),
                                    ),
                                ),
                            )
                            .subscribe(() => {
                                if (this.codeEditorContainer) {
                                    this.codeEditorContainer.editorState = EditorState.CLEAN;
                                }
                                cleanup();
                                this.codeGenAlertService.addAlert({
                                    type: event.success ? AlertType.SUCCESS : AlertType.WARNING,
                                    translationKey: event.success
                                        ? 'artemisApp.programmingExercise.codeGeneration.success'
                                        : 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                                    translationParams: { repositoryType: this.selectedRepository },
                                });
                            });
                        break;

                    case 'ERROR':
                        cleanup();
                        this.codeGenAlertService.addAlert({
                            type: AlertType.DANGER,
                            translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                            translationParams: { repositoryType: this.selectedRepository },
                        });
                        break;

                    default:
                }
            },
            error: () => {
                cleanup();
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    translationParams: { repositoryType: this.selectedRepository },
                });
            },
            complete: () => {
                // don't auto-stop spinner here; DONE/ERROR/timeout handle it
            },
        });

        // Safety timeout (20 minutes)
        this.jobTimeoutHandle = window.setTimeout(() => {
            if (this.isGeneratingCode()) {
                cleanup();
                this.codeGenAlertService.addAlert({
                    type: AlertType.WARNING,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout',
                });
            }
        }, 1_200_000);
    }

    /**
     * Checks whether a consistency check operation is currently running.
     *
     * @returns {boolean} `true` if either the rewrite or consistency check process is currently loading; otherwise `false`.
     */
    isCheckingConsistency(): boolean {
        return this.artemisIntelligenceService.isLoading();
    }

    /**
     * Runs a consistency check for the given programming exercise.
     *
     * First verifies that all required repositories are set up correctly.
     * If no setup issues are found, performs a full content consistency check.
     * Displays alerts for errors, warnings, or successful results.
     *
     * @param {ProgrammingExercise} exercise - The exercise to check.
     */
    checkConsistencies(exercise: ProgrammingExercise) {
        // Clear previous consistency issues
        this.consistencyIssues.set([]);

        if (!exercise.id) {
            this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
            return;
        }

        this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id!).subscribe({
            // This first consistency check ensures, that the exercise has all repositories set up
            // This does not yet check the actual content of the exercise
            next: (inconsistencies: ConsistencyCheckError[]) => {
                if (inconsistencies.length > 0) {
                    for (const inconsistency of inconsistencies) {
                        this.alertService.error(this.translateService.instant(`artemisApp.consistencyCheck.error.${inconsistency.type}`));
                    }
                    return;
                }

                // Now the content is checked
                this.artemisIntelligenceService.consistencyCheck(exercise.id!).subscribe({
                    next: (response: ConsistencyCheckResponse) => {
                        this.consistencyIssues.set(response.issues ?? []);

                        if (this.consistencyIssues().length === 0) {
                            this.alertService.success(this.translateService.instant('artemisApp.consistencyCheck.noInconsistencies'));
                        } else {
                            this.alertService.warning(this.translateService.instant('artemisApp.consistencyCheck.inconsistenciesFoundAlert'));
                        }
                    },
                    error: () => {
                        this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
                    },
                });
            },
            error: (err) => {
                this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
            },
        });
    }
}

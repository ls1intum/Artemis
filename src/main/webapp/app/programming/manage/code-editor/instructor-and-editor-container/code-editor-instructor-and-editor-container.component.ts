import { Component, ViewChild, inject } from '@angular/core';
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
import { signal } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { HttpClient } from '@angular/common/http';
import { Subscription, catchError, of, switchMap, take } from 'rxjs';

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

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;
    faSpinner = faSpinner;
    facArtemisIntelligence = facArtemisIntelligence;
    irisSettings?: IrisSettings;
    protected readonly RepositoryType = RepositoryType;
    private codeGenAlertService = inject(AlertService);
    private profileService = inject(ProfileService);
    private modalService = inject(NgbModal);
    private hyperionWs = inject(HyperionWebsocketService);
    private repoService = inject(CodeEditorRepositoryService);
    private repoFileService = inject(CodeEditorRepositoryFileService);
    private http = inject(HttpClient);
    isGeneratingCode = signal(false);
    private jobSubscription?: Subscription;
    private jobTimeoutHandle?: number;

    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

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
        //console.log('Exercise ID:', this.exercise?.id);
        //console.log('Repository type:', this.selectedRepository);
        this.http.post<{ jobId: string }>(`api/hyperion/programming-exercises/${this.exercise!.id}/generate-code`, request).subscribe({
            next: (res) => {
                //console.log('✅ Response received from backend:', res);
                if (!res?.jobId) {
                    //console.warn('⚠️ No jobId found in response, stopping spinner.');
                    this.isGeneratingCode.set(false);
                    this.codeGenAlertService.addAlert({
                        type: AlertType.DANGER,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    });
                    return;
                }
                //console.log('📬 Received jobId:', res.jobId);
                //console.log('🔗 Subscribing to job via websocket...');
                this.subscribeToJob(res.jobId);
            },
            error: (err) => {
                //console.error('❌ HTTP request failed:', err);
                this.isGeneratingCode.set(false);
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                });
            },
            complete: () => {
                //console.log('ℹ️ HTTP observable completed.');
            },
        });
    }

    /**
     * Subscribes to job updates, refreshes files on updates, and stops spinner on terminal events.
     * @param jobId job identifier
     */
    private subscribeToJob(jobId: string) {
        //const channel = `/user/topic/hyperion/code-generation/jobs/${jobId}`;
        // console.log('[Hyperion] Subscribing to channel:', channel);

        const cleanup = (reason: string) => {
            //console.log('[Hyperion] Cleaning up subscription. Reason =', reason);
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
                //console.log('[Hyperion WS event]', event);

                switch (event.type) {
                    case 'STARTED':
                        // spinner already on; just log
                        //console.log('[Hyperion] Job started.');
                        break;

                    case 'PROGRESS':
                        //console.log('[Hyperion] Progress iteration:', event.iteration);
                        break;

                    case 'FILE_UPDATED':
                    case 'NEW_FILE':
                        //console.log(`[Hyperion] ${event.type} -> pulling repo…`, event.path);
                        this.repoService
                            .pull()
                            .pipe(
                                take(1),
                                catchError((err) => {
                                    //console.error('[Hyperion] Repo pull failed on update:', err);
                                    return of(void 0);
                                }),
                            )
                            .subscribe(() => {
                                //console.log('[Hyperion] Repo pull done after', event.type);
                            });
                        break;

                    case 'DONE':
                        //console.log('[Hyperion] DONE event received. success =', event.success, 'attempts =', event.attempts);
                        if (this.codeEditorContainer) {
                            this.codeEditorContainer.editorState = EditorState.REFRESHING;
                        }
                        this.repoService
                            .pull()
                            .pipe(
                                take(1),
                                catchError((err) => {
                                    //console.error('[Hyperion] Final repo pull failed:', err);
                                    return of(void 0);
                                }),
                                switchMap(() =>
                                    this.repoFileService.getRepositoryContent().pipe(
                                        take(1),
                                        catchError((err) => {
                                            //console.error('[Hyperion] Refresh repository content failed:', err);
                                            return of({});
                                        }),
                                    ),
                                ),
                            )
                            .subscribe(() => {
                                if (this.codeEditorContainer) {
                                    this.codeEditorContainer.editorState = EditorState.CLEAN;
                                }
                                cleanup('DONE');
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
                        //console.error('[Hyperion] ERROR event:', event.message);
                        cleanup('ERROR');
                        this.codeGenAlertService.addAlert({
                            type: AlertType.DANGER,
                            translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                            translationParams: { repositoryType: this.selectedRepository },
                        });
                        break;

                    default:
                    //console.warn('[Hyperion] Unhandled event type:', (event as any).type);
                }
            },
            error: (err) => {
                //console.error('[Hyperion] WebSocket stream error:', err);
                cleanup('WS_ERROR');
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    translationParams: { repositoryType: this.selectedRepository },
                });
            },
            complete: () => {
                //console.log('[Hyperion] WebSocket stream completed.');
                // don't auto-stop spinner here; DONE/ERROR/timeout handle it
            },
        });

        // Safety timeout (10 minutes)
        this.jobTimeoutHandle = window.setTimeout(() => {
            if (this.isGeneratingCode()) {
                //console.warn('[Hyperion] Job timeout reached — stopping spinner & unsubscribing. jobId =', jobId);
                cleanup('TIMEOUT');
                this.codeGenAlertService.addAlert({
                    type: AlertType.WARNING,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout',
                });
            }
        }, 600_000);
    }
}

import { Component, ViewChild } from '@angular/core';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCircleNotch, faPlus, faSpinner, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { CodeGenerationRequestDTO } from 'app/openapi/model/codeGenerationRequestDTO';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { inject, signal } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';

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

    private codeGenerationService = inject(HyperionCodeGenerationApiService);
    private codeGenAlertService = inject(AlertService);
    private profileService = inject(ProfileService);
    isGeneratingCode = signal(false);

    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

    /**
     * Generates code for the current programming exercise using AI
     */
    generateCode(): void {
        if (!this.exercise?.id || this.isGeneratingCode()) {
            return;
        }

        // Only allow code generation for supported repository types
        if (this.selectedRepository !== RepositoryType.TEMPLATE && this.selectedRepository !== RepositoryType.SOLUTION && this.selectedRepository !== RepositoryType.TESTS) {
            this.codeGenAlertService.addAlert({
                type: AlertType.WARNING,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.unsupportedRepository',
            });
            return;
        }

        this.isGeneratingCode.set(true);

        const request: CodeGenerationRequestDTO = {
            repositoryType: this.selectedRepository as CodeGenerationRequestDTO.RepositoryTypeEnum,
        };

        this.codeGenerationService.generateCode(this.exercise.id, request).subscribe({
            next: (response) => {
                this.isGeneratingCode.set(false);

                if (response.success) {
                    this.codeGenAlertService.addAlert({
                        type: AlertType.SUCCESS,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
                        translationParams: { repositoryType: this.selectedRepository },
                    });
                } else {
                    this.codeGenAlertService.addAlert({
                        type: AlertType.WARNING,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                        translationParams: { repositoryType: this.selectedRepository },
                    });
                }
            },
            error: () => {
                this.isGeneratingCode.set(false);
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    translationParams: { repositoryType: this.selectedRepository },
                });
            },
        });
    }
}

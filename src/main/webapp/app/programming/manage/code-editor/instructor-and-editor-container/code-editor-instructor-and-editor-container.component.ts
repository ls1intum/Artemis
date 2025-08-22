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
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CodeGenerationService } from 'app/hyperion/code-generation.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { inject } from '@angular/core';
import { CodeGenerationAction } from 'app/hyperion/actions/code-generation.action';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';

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

    // Code Generation
    private codeGenerationService = inject(CodeGenerationService);
    private codeGenAlertService = inject(AlertService);
    isGeneratingCode = false;

    // AI Intelligence Actions - mimics the pattern from ProgrammingExerciseEditableInstructionComponent
    artemisIntelligenceActions: TextEditorAction[] = [new CodeGenerationAction(() => this.generateCode())];

    /**
     * Generates code for the current programming exercise using AI
     */
    generateCode(): void {
        if (!this.exercise?.id || this.isGeneratingCode) {
            return;
        }

        this.isGeneratingCode = true;

        this.codeGenerationService.generateCode(this.exercise.id).subscribe({
            next: (response) => {
                this.isGeneratingCode = false;

                if (response.success) {
                    this.codeGenAlertService.addAlert({
                        type: AlertType.SUCCESS,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
                    });
                } else {
                    this.codeGenAlertService.addAlert({
                        type: AlertType.WARNING,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                    });
                }
            },
            error: () => {
                this.isGeneratingCode = false;
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                });
            },
        });
    }
}

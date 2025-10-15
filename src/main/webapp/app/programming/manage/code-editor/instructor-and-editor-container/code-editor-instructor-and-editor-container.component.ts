import { Component, ViewChild, effect, inject, signal } from '@angular/core';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCircleNotch, faPlus, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faCheckDouble } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckComponent } from 'app/programming/manage/consistency-check/consistency-check.component';
import { ConsistencyCheckAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check.action';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
    styleUrl: 'code-editor-instructor-and-editor-container.scss',
    imports: [
        FaIconComponent,
        TranslateDirective,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
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
    irisSettings?: IrisSettings;
    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faCheckDouble = faCheckDouble;

    private consistencyCheckService = inject(ConsistencyCheckService);
    private consistencyIssues = signal<ConsistencyIssue[]>([]);
    private modalService = inject(NgbModal);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);

    constructor() {
        super();

        effect(() => {
            const issues = this.consistencyIssues();
            for (const issue of issues) {
                for (const loc of issue.relatedLocations) {
                    this.codeEditorContainer.monacoEditor.addCommentBox(loc.endLine, issue.description);
                }
            }
        });
    }

    checkConsistencies(exercise: ProgrammingExercise) {
        this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id!).subscribe({
            next: (inconsistencies) => {
                if (inconsistencies?.length) {
                    // only show modal if inconsistencies found
                    const modalRef = this.modalService.open(ConsistencyCheckComponent, { keyboard: true, size: 'lg' });
                    modalRef.componentInstance.exercisesToCheck = [exercise];
                    return;
                }

                const action = new ConsistencyCheckAction(this.artemisIntelligenceService, exercise.id!, this.codeEditorContainer.monacoEditor.consistencyIssuesInternal);
                this.codeEditorContainer.monacoEditor.editor().registerAction(action);
                action.executeInCurrentEditor();

                const action2 = new ConsistencyCheckAction(
                    this.artemisIntelligenceService,
                    exercise.id!,
                    this.editableInstructions.markdownEditorMonaco?.consistencyIssuesInternal!,
                );
                this.editableInstructions.markdownEditorMonaco?.monacoEditor?.registerAction(action2);
                action2.executeInCurrentEditor();
            },
            error: (err) => {
                const modalRef = this.modalService.open(ConsistencyCheckComponent, { keyboard: true, size: 'lg' });
                modalRef.componentInstance.exercisesToCheck = [exercise];
            },
        });
    }
}

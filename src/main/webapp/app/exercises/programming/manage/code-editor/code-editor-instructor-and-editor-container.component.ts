import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent, REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { faCircleNotch, faPlus, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { IrisCodeEditorWebsocketService, StepExecutionException, StepExecutionSuccess } from 'app/iris/code-editor-websocket.service';
import { IrisCodeEditorChatbotButtonComponent } from 'app/iris/exercise-chatbot/code-editor-chatbot-button.component';
import { ExerciseComponent } from 'app/entities/iris/iris-content-type.model';

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent {
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;
    @ViewChild(ProgrammingExerciseEditableInstructionComponent, { static: false }) editableInstructions: ProgrammingExerciseEditableInstructionComponent;
    @ViewChild(IrisCodeEditorChatbotButtonComponent, { static: false }) chatbotButton: IrisCodeEditorChatbotButtonComponent;

    readonly IncludedInOverallScore = IncludedInOverallScore;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    constructor(
        router: Router,
        exerciseService: ProgrammingExerciseService,
        courseExerciseService: CourseExerciseService,
        domainService: DomainService,
        programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        location: Location,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
        alertService: AlertService,
        codeEditorWebsocketService: IrisCodeEditorWebsocketService,
    ) {
        super(router, exerciseService, courseExerciseService, domainService, programmingExerciseParticipationService, location, participationService, route, alertService);
        codeEditorWebsocketService.onPromptReload().subscribe((filesChanged: StepExecutionSuccess) => {
            this.handleChangeNotification(filesChanged);
        });
        codeEditorWebsocketService.onStepException().subscribe((stepException: StepExecutionException) => {
            this.handleExceptionNotification(stepException);
        });
    }

    toRepository(component: ExerciseComponent) {
        switch (component) {
            case ExerciseComponent.TEMPLATE_REPOSITORY:
                return REPOSITORY.TEMPLATE;
            case ExerciseComponent.SOLUTION_REPOSITORY:
                return REPOSITORY.SOLUTION;
            case ExerciseComponent.TEST_REPOSITORY:
                return REPOSITORY.TEST;
            default:
                throw new Error(`Problem statement is not a repository!`);
        }
    }

    private handleChangeNotification(success: StepExecutionSuccess) {
        if (success.component == ExerciseComponent.PROBLEM_STATEMENT) {
            this.editableInstructions.updateProblemStatement(success.updatedProblemStatement!);
        } else {
            const repository = this.toRepository(success.component);
            if (!this.selectedRepository || this.selectedRepository === repository) {
                this.codeEditorContainer.aceEditor.forceReloadAll(success.paths!);
            }
        }
        // Find the corresponding plan and step and execute the next step, if there is one.
        // Also, the plan's currentStepIndex must be updated so that the chatbot widget can display the correct step as in progress.
        const widget = this.chatbotButton?.dialogRef?.componentRef?.instance; // Access the widget via the button even if it is not open
        widget?.notifyStepCompleted(success.messageId, success.planId, success.stepId);
    }

    private handleExceptionNotification(exception: StepExecutionException) {
        // Find the corresponding plan and failed step and execute the next step, if there is one.
        const widget = this.chatbotButton?.dialogRef?.componentRef?.instance; // Access the widget via the button even if it is not open
        widget?.notifyStepFailed(exception.messageId, exception.planId, exception.stepId, exception.errorTranslationKey!, exception.translationParams!);
    }

    onResizeEditorInstructions() {
        if (this.editableInstructions.markdownEditor && this.editableInstructions.markdownEditor.aceEditorContainer) {
            this.editableInstructions.markdownEditor.aceEditorContainer.getEditor().resize();
        }
    }
}

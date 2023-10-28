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
import { ExerciseComponent, FilesChanged, IrisCodeEditorWebsocketService } from 'app/iris/code-editor-websocket.service';
import { IrisCodeEditorChatbotButtonComponent } from 'app/iris/exercise-chatbot/code-editor-chatbot-button.component';
import { IrisExercisePlan } from 'app/entities/iris/iris-content-type.model';

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
        codeEditorWebsocketService.onPromptReload().subscribe((changeNotification: FilesChanged) => {
            this.handleChangeNotification(changeNotification);
        });
    }

    private toRepository(component: ExerciseComponent) {
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

    private handleChangeNotification(filesChanged: FilesChanged) {
        if (filesChanged.component == ExerciseComponent.PROBLEM_STATEMENT) {
            this.editableInstructions.updateProblemStatement(filesChanged.updatedProblemStatement!);
        } else {
            const repository = this.toRepository(filesChanged.component);
            if (this.selectedRepository && this.selectedRepository !== repository) {
                // The user is not looking at the repository that the changes were for.
                // We don't have to reload anything because the user will see the changes when they switch to the correct repository.
                return;
            }
            for (const path of filesChanged.paths!) {
                // Force a reload of each file that was changed.
                // This will cause any changes since the request to Iris to be lost.
                // Maybe we can find a way to merge the changes, but for now this is the simplest solution.
                delete this.codeEditorContainer?.aceEditor?.fileSession[path];
            }
        }
        // Find the corresponding plan and step and execute the next step, if there is one.
        // Also, the plan's currentStepIndex must be updated so that the chatbot widget can display the correct step as in progress.
        const widget = this.chatbotButton?.dialogRef?.componentRef?.instance; // Access the widget via the button even if it is not open
        const message = widget?.messages.find((m) => m.id === filesChanged.messageId);
        const plan = message?.content.find((c) => c.id === filesChanged.planId) as IrisExercisePlan;
        if (!widget) {
            console.error('Received change notification but could not access chatbot widget to forward it.');
            return;
        }
        if (!message) {
            console.error('Received change notification but could not find corresponding message.');
            return;
        }
        if (!plan) {
            console.error('Received change notification but could not find corresponding plan.');
            return;
        }
        // Search through steps in the plan until we find the one that was executed
        for (let i = 0; i < plan.steps.length; i++) {
            const step = plan.steps[i];
            if (step.id === filesChanged.stepId) {
                // Success! Found the corresponding step.
                plan.currentStepIndex = i; // Update the current step index
                if (i < plan.steps.length - 1) {
                    // Execute the next step if the user has not paused the execution
                    // TODO: Implement plan pausing
                    const nextStep = plan.steps[i + 1];
                    widget.executePlanStep(filesChanged.messageId, filesChanged.planId, nextStep.id!);
                }
                return;
            }
        }
        console.error('Received change notification but could not find corresponding step.');
    }

    onResizeEditorInstructions() {
        if (this.editableInstructions.markdownEditor && this.editableInstructions.markdownEditor.aceEditorContainer) {
            this.editableInstructions.markdownEditor.aceEditorContainer.getEditor().resize();
        }
    }
}

import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { faCircleNotch, faPlus, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ChangeNotification, IrisCodeEditorWebsocketService } from 'app/iris/code-editor-websocket.service';
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
        codeEditorWebsocketService.onPromptReload().subscribe((changeNotification: ChangeNotification) => {
            this.handleChangeNotification(changeNotification);
        });
    }

    private handleChangeNotification(changeNotification: ChangeNotification) {
        if (changeNotification.updatedProblemStatement) {
            this.editableInstructions.updateProblemStatement(changeNotification.updatedProblemStatement);
        } else {
            // Reload the code editor to display new code changes
            this.codeEditorContainer.aceEditor.initEditor();
        }
        // Find the corresponding plan and step and execute the next step, if there is one.
        // Also, the plan's currentStepIndex must be updated so that the chatbot widget can display the correct step as in progress.
        const widget = this.chatbotButton?.dialogRef?.componentRef?.instance;
        const message = widget?.messages.find((m) => m.id === changeNotification.messageId);
        const plan = message?.content.find((c) => c.id === changeNotification.planId) as IrisExercisePlan;
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
        for (let i = 0; i < plan.steps.length; i++) {
            const step = plan.steps[i];
            if (step.id === changeNotification.stepId) {
                // Success! Found the corresponding step.
                plan.currentStepIndex = i; // Update the current step index
                if (i < plan.steps.length - 1) {
                    // Execute the next step
                    const nextStep = plan.steps[i + 1];
                    widget.executePlanStep(changeNotification.messageId, changeNotification.planId, nextStep.id!);
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

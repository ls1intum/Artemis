import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpCodeEditorSessionService } from 'app/iris/http-code-editor-session.service';
import { IrisHttpCodeEditorMessageService, UnsavedChangesDTO } from 'app/iris/http-code-editor-message.service';
import { IrisMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { firstValueFrom } from 'rxjs';
import { IrisExercisePlanStep } from 'app/entities/iris/iris-exercise-plan-component.model';

/**
 * The IrisCodeEditorSessionService is responsible for managing Iris code editor sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisCodeEditorSessionService extends IrisSessionService {
    /**
     * Uses the IrisHttpCodeEditorSessionService and IrisHttpCodeEditorMessageService to retrieve and manage Iris code editor sessions.
     */
    constructor(
        stateStore: IrisStateStore,
        irisSessionService: IrisHttpCodeEditorSessionService,
        private irisHttpCodeEditorMessageService: IrisHttpCodeEditorMessageService,
    ) {
        super(stateStore, irisSessionService, irisHttpCodeEditorMessageService);
    }

    /**
     * Sends a message to the server and returns the created message.
     * @param sessionId of the session in which the message should be created
     * @param message to be created
     * @param unsavedChanges the unsaved changes the user has made in the editor
     */
    async sendMessage(sessionId: number, message: IrisUserMessage, unsavedChanges: UnsavedChangesDTO): Promise<IrisMessage> {
        const response = await firstValueFrom(this.irisHttpCodeEditorMessageService.createMessage(sessionId, message, unsavedChanges));
        return response.body!;
    }

    /**
     * Resends a message to the server and returns the created message.
     * @param sessionId of the session in which the message should be created
     * @param message to be created
     * @param unsavedChanges the unsaved changes the user has made in the editor
     */
    async resendMessage(sessionId: number, message: IrisUserMessage, unsavedChanges: UnsavedChangesDTO): Promise<IrisMessage> {
        const response = await firstValueFrom(this.irisHttpCodeEditorMessageService.resendMessage(sessionId, message, unsavedChanges));
        return response.body!;
    }

    /**
     * Executes one step of an exercise plan.
     * @param sessionId of the session in which the exercise plan exists
     * @param messageId of the message which contains the exercise plan
     * @param planId of the exercise plan
     * @param stepId of the step to be executed
     * @param unsavedChanges the unsaved changes the user has made in the editor
     */
    async executePlanStep(sessionId: number, messageId: number, planId: number, stepId: number, unsavedChanges: UnsavedChangesDTO): Promise<void> {
        await firstValueFrom(this.irisHttpCodeEditorMessageService.executePlanStep(sessionId, messageId, planId, stepId, unsavedChanges));
    }

    /**
     * Updates the instructions of an exercise plan step and returns the updated step.
     * @param sessionId of the session in which the exercise plan exists
     * @param messageId of the message which contains the exercise plan
     * @param planId of the exercise plan
     * @param stepId of the step to be updated
     * @param step with the updated instructions
     */
    async updatePlanStepInstructions(sessionId: number, messageId: number, planId: number, stepId: number, step: IrisExercisePlanStep): Promise<IrisExercisePlanStep> {
        const response = await firstValueFrom(this.irisHttpCodeEditorMessageService.updateExercisePlanStepInstructions(sessionId, messageId, planId, stepId, step));
        return response.body!;
    }
}

import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpCodeEditorSessionService } from 'app/iris/http-code-editor-session.service';
import { IrisHttpCodeEditorMessageService } from 'app/iris/http-code-editor-message.service';
import { firstValueFrom } from 'rxjs';
import { IrisExercisePlanStep } from 'app/entities/iris/iris-content-type.model';

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
     * Executes one step of an exercise plan.
     * @param sessionId of the session in which the exercise plan exists
     * @param messageId of the message which contains the exercise plan
     * @param planId of the exercise plan
     * @param stepId of the step to be executed
     */
    async executePlanStep(sessionId: number, messageId: number, planId: number, stepId: number): Promise<void> {
        await firstValueFrom(this.irisHttpCodeEditorMessageService.executePlanStep(sessionId, messageId, planId, stepId));
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

import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpCodeEditorSessionService } from 'app/iris/http-code-editor-session.service';
import { IrisHttpCodeEditorMessageService, UnsavedChangesDTO } from 'app/iris/http-code-editor-message.service';
import { IrisClientMessage, IrisMessage } from 'app/entities/iris/iris-message.model';
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
        private irisHttpCodeEditorSessionService: IrisHttpCodeEditorSessionService,
        private irisHttpCodeEditorMessageService: IrisHttpCodeEditorMessageService,
    ) {
        super(stateStore, irisHttpCodeEditorSessionService, irisHttpCodeEditorMessageService);
    }

    async createMessage(sessionId: number, message: IrisClientMessage, unsavedChanges: UnsavedChangesDTO): Promise<IrisMessage> {
        const response = await firstValueFrom(this.irisHttpCodeEditorMessageService.createMessage(sessionId, message, unsavedChanges));
        return response.body!;
    }

    async resendMessage(sessionId: number, message: IrisClientMessage, unsavedChanges: UnsavedChangesDTO): Promise<IrisMessage> {
        const response = await firstValueFrom(this.irisHttpCodeEditorMessageService.resendMessage(sessionId, message, unsavedChanges));
        return response.body!;
    }

    async executePlanStep(sessionId: number, messageId: number, planId: number, stepId: number, unsavedChanges: UnsavedChangesDTO): Promise<IrisMessage> {
        const response = await firstValueFrom(this.irisHttpCodeEditorMessageService.executePlanStep(sessionId, messageId, planId, stepId, unsavedChanges));
        return response.body!;
    }

    async updatePlanStepInstructions(sessionId: number, messageId: number, planId: number, stepId: number, step: IrisExercisePlanStep): Promise<IrisExercisePlanStep> {
        const response = await firstValueFrom(this.irisHttpCodeEditorMessageService.updateExercisePlanStepInstructions(sessionId, messageId, planId, stepId, step));
        return response.body!;
    }
}

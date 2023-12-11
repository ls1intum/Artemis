import { Injectable } from '@angular/core';
import { IrisExercisePlanStep } from 'app/entities/iris/iris-content-type.model';
import { IrisHttpMessageService, Response } from 'app/iris/http-message.service';

/**
 * Provides an additional set of methods specific to the Code Editor Chatbot.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpCodeEditorMessageService extends IrisHttpMessageService {
    private readonly sessionType = 'code-editor-sessions';

    /**
     * Execute the exercise plan, i.e. request the changes to be applied to the code editor.
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param planId of the exercise plan
     * @param executing of the exercise plan step
     */
    setPlanExecuting(sessionId: number, messageId: number, planId: number, executing: boolean): Response<boolean> {
        return this.httpClient.post<boolean>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${messageId}/contents/${planId}/executing/${executing}`, null, {
            observe: 'response',
        });
    }

    /**
     * Execute the exercise plan, i.e. request the changes to be applied to the code editor.
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param planId of the exercise plan
     * @param stepId of the exercise plan step
     */
    executePlanStep(sessionId: number, messageId: number, planId: number, stepId: number): Response<void> {
        return this.httpClient.post<void>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${messageId}/contents/${planId}/steps/${stepId}/execute`, null, {
            observe: 'response',
        });
    }

    /**
     * Update a component of an exercise plan
     * @param sessionId     of the session
     * @param messageId     of the message
     * @param planId        of the exercise plan
     * @param stepId        of the exercise plan step
     * @param updatedStep   the step with the updated instructions
     * @return {Response<IrisExercisePlanStep>} an Observable of the HTTP responses
     */
    updateExercisePlanStepInstructions(sessionId: number, messageId: number, planId: number, stepId: number, updatedStep: IrisExercisePlanStep): Response<IrisExercisePlanStep> {
        return this.httpClient.put<IrisExercisePlanStep>(
            `${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${messageId}/contents/${planId}/steps/${stepId}`,
            updatedStep,
            { observe: 'response' },
        );
    }
}

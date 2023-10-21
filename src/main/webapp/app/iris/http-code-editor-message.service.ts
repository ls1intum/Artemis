import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IrisExercisePlanStep } from 'app/entities/iris/iris-exercise-plan-component.model';
import { IrisHttpMessageService, Response } from 'app/iris/http-message.service';
import { IrisClientMessage, IrisMessage, IrisServerMessage } from 'app/entities/iris/iris-message.model';
import { convertDateFromClient } from 'app/utils/date.utils';
import { tap } from 'rxjs/operators';

export class UnsavedChangesDTO {
    problemStatement?: string;
    templateRepository?: Map<string, string>;
    solutionRepository?: Map<string, string>;
    testRepository?: Map<string, string>;
}

/**
 * Provides an additional set of methods specific to the Code Editor Chatbot.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpCodeEditorMessageService extends IrisHttpMessageService {
    constructor(httpClient: HttpClient) {
        super(httpClient, 'code-editor-sessions');
    }

    /**
     * creates a message for a code editor session
     * @param sessionId of the session
     * @param message  to be created
     * @param unsavedChanges unsaved changes from the editor which the AI should know about
     */
    createMessage(sessionId: number, message: IrisClientMessage, unsavedChanges: UnsavedChangesDTO): Response<IrisMessage> {
        message.messageDifferentiator = this.randomInt();
        return this.httpClient
            .post<IrisServerMessage>(
                `${this.apiPrefix}/${this.sessionType}/${sessionId}/messages`,
                {
                    message: Object.assign({}, message, {
                        sentAt: convertDateFromClient(message.sentAt),
                    }),
                    unsavedChanges: unsavedChanges,
                },
                { observe: 'response' },
            )
            .pipe(
                tap((response) => {
                    if (response.body && response.body.id) {
                        message.id = response.body.id;
                    }
                }),
            );
    }

    /**
     * resends a message for a code editor session
     * @param sessionId of the session
     * @param message to be resent
     * @param unsavedChanges unsaved changes from the editor which the AI should know about
     * @return {Response<IrisMessage>}
     */
    resendMessage(sessionId: number, message: IrisClientMessage, unsavedChanges: UnsavedChangesDTO): Response<IrisMessage> {
        message.messageDifferentiator = message.messageDifferentiator ?? this.randomInt();
        return this.httpClient
            .post<IrisServerMessage>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${message.id}/resend`, unsavedChanges, { observe: 'response' })
            .pipe(
                tap((response) => {
                    if (response.body && response.body.id) {
                        message.id = response.body.id;
                    }
                }),
            );
    }

    /**
     * Execute the exercise plan, i.e. request the changes to be applied to the code editor.
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param planId of the exercise plan
     * @param stepId of the exercise plan step
     * @param unsavedChanges the unsaved changes from the editor which should be considered when executing the plan
     */
    executePlanStep(sessionId: number, messageId: number, planId: number, stepId: number, unsavedChanges: UnsavedChangesDTO): Response<void> {
        return this.httpClient.post<void>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${messageId}/contents/${planId}/steps/${stepId}/execute`, unsavedChanges, {
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

import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisExercisePlanComponent } from 'app/entities/iris/iris-exercise-plan-component.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';

type EntityPlanResponseType = HttpResponse<IrisExercisePlanComponent>;

/**
 * Provides an additional set of methods specific to the Code Editor Chatbot.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpCodeEditorMessageService extends IrisHttpMessageService {
    constructor(httpClient: HttpClient) {
        super(httpClient, 'code-editor-sessions');
    }

    /**
     * Execute the exercise plan, i.e. request the changes to be applied to the code editor.
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param contentId of the content
     */
    executePlan(sessionId: number, messageId: number, contentId: number): Observable<HttpResponse<void>> {
        return this.httpClient.post<void>(`${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${messageId}/contents/${contentId}/execute`, null, { observe: 'response' });
    }

    /**
     * Update a component of an exercise plan
     * @param sessionId   of the session
     * @param messageId   of the message
     * @param contentId   of the (exercise plan) message content
     * @param componentId of the exercisePlanComponent
     * @param component   with the updated values
     * @return {Observable<EntityPlanResponseType>} an Observable of the HTTP responses
     */
    updateExercisePlanComponent(
        sessionId: number,
        messageId: number,
        contentId: number,
        componentId: number,
        component: IrisExercisePlanComponent,
    ): Observable<EntityPlanResponseType> {
        return this.httpClient.put<IrisExercisePlanComponent>(
            `${this.apiPrefix}/${this.sessionType}/${sessionId}/messages/${messageId}/contents/${contentId}/components/${componentId}`,
            component,
            { observe: 'response' },
        );
    }
}

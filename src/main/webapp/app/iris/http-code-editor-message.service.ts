import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisExercisePlanComponent } from 'app/entities/iris/iris-exercise-plan-component.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';

type EntityPlanResponseType = HttpResponse<IrisExercisePlanComponent>;

/**
 * Provides a singleton root-level IrisHttpMessageService to perform CRUD operations on messages
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpCodeEditorMessageService extends IrisHttpMessageService {
    constructor(httpClient: HttpClient) {
        super(httpClient, 'api/iris/code-editor-sessions');
    }

    /**
     * Send the (updated) plan message to the LLM
     *
     * @param sessionId of the session
     * @param messageId of the message
     * @param contentId of the content
     */
    executePlan(sessionId: number, messageId: number, contentId: number): Observable<HttpResponse<void>> {
        return this.httpClient.post<void>(`${this.resourceUrl}/${sessionId}/messages/${messageId}/contents/${contentId}/execute`, {}, { observe: 'response' });
    }

    /**
     * update the component plan
     * @param {number} sessionId of the session of the message that should be rated
     * @param messageId   of the message
     * @param contentId   of the content
     * @param componentId of the exercisePlanComponent
     * @param component   to set for the corresponding component
     * @return {Observable<EntityPlanResponseType>} an Observable of the HTTP responses
     */
    updateComponentPlan(sessionId: number, messageId: number, contentId: number, componentId: number, component: IrisExercisePlanComponent): Observable<EntityPlanResponseType> {
        return this.httpClient.put<IrisExercisePlanComponent>(`${this.resourceUrl}/${sessionId}/messages/${messageId}/contents/${contentId}/components/${componentId}`, component, {
            observe: 'response',
        });
    }
}

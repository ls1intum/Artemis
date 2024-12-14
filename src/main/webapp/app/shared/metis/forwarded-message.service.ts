import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ForwardedMessage } from 'app/entities/metis/forwarded-message.model';

type EntityResponseType = HttpResponse<ForwardedMessage>;

@Injectable({ providedIn: 'root' })
export class ForwardedMessageService {
    public resourceUrl = 'api/forwarded-messages';

    protected http: HttpClient = inject(HttpClient);

    /**
     * Creates a new ForwardedMessage.
     * @param forwardedMessage The ForwardedMessage to create.
     * @returns An observable containing the created ForwardedMessage wrapped in an HttpResponse.
     */
    createForwardedMessage(forwardedMessage: ForwardedMessage): Observable<EntityResponseType> {
        const copy = this.convertForwardedMessage(forwardedMessage);
        return this.http.post<ForwardedMessage>(`${this.resourceUrl}`, copy, { observe: 'response' }).pipe(map((res: HttpResponse<ForwardedMessage>) => this.convertResponse(res)));
    }

    /**
     * Retrieves forwarded messages for a given set of IDs and message type.
     *
     * @param ids - An array of numeric IDs for which forwarded messages should be retrieved.
     * @param type - The type of messages to retrieve ('post' or 'answer').
     * @returns An observable containing a list of objects where each object includes an ID and its corresponding messages, wrapped in an HttpResponse.
     */
    getForwardedMessages(ids: number[], type: 'post' | 'answer'): Observable<HttpResponse<{ id: number; messages: ForwardedMessage[] }[]>> {
        if (!ids || ids.length === 0) {
            throw new Error('IDs cannot be empty');
        }

        const params = new HttpParams().set('ids', ids.join(',')).set('type', type);

        return this.http
            .get<{ id: number; messages: ForwardedMessage[] }[]>('api/forwarded-messages', {
                params,
                observe: 'response',
            })
            .pipe();
    }

    /**
     * Converts the response to a client-compatible ForwardedMessage object.
     *
     * @param res - The HttpResponse object containing a ForwardedMessage from the backend.
     * @returns A cloned HttpResponse with the body converted to a ForwardedMessage object.
     */
    private convertResponse(res: HttpResponse<ForwardedMessage>): HttpResponse<ForwardedMessage> {
        const body: ForwardedMessage = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertItemFromServer(forwardedMessage: ForwardedMessage): ForwardedMessage {
        return Object.assign({}, forwardedMessage);
    }

    private convertForwardedMessage(forwardedMessage: ForwardedMessage): ForwardedMessage {
        return Object.assign({}, forwardedMessage);
    }
}

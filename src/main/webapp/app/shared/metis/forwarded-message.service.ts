import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ForwardedMessage } from 'app/entities/metis/forwarded-message.model';

type EntityResponseType = HttpResponse<ForwardedMessage>;

@Injectable({ providedIn: 'root' })
export class ForwardedMessageService {
    public resourceUrl = 'api/forwarded-messages';

    constructor(protected http: HttpClient) {}

    /**
     * Creates a new ForwardedMessage.
     * @param forwardedMessage The ForwardedMessage to create.
     * @returns The created ForwardedMessage.
     */
    createForwardedMessage(forwardedMessage: ForwardedMessage): Observable<EntityResponseType> {
        const copy = this.convertForwardedMessage(forwardedMessage);
        return this.http.post<ForwardedMessage>(`${this.resourceUrl}`, copy, { observe: 'response' }).pipe(map((res: HttpResponse<ForwardedMessage>) => this.convertResponse(res)));
    }

    getForwardedMessages(postIds: number[]): Observable<HttpResponse<Map<number, ForwardedMessage[]>>> {
        const params = new HttpParams().set('dest_post_ids', postIds.join(','));
        return this.http
            .get<Map<number, ForwardedMessage[]>>(`api/forwarded-messages/posts`, {
                params,
                observe: 'response',
            })
            .pipe();
    }

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

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { ForwardedMessage, ForwardedMessageDTO } from 'app/communication/shared/entities/forwarded-message.model';
import { PostingType } from 'app/communication/shared/entities/posting.model';

type EntityResponseType = HttpResponse<ForwardedMessageDTO>;

@Injectable({ providedIn: 'root' })
export class ForwardedMessageService {
    public resourceUrl = 'api/communication/forwarded-messages';

    private http = inject(HttpClient);

    /**
     * Sends a request to create a new forwarded message.
     *
     * @param forwardedMessage The message to be forwarded.
     * @returns An observable containing the HTTP response with the created forwarded message.
     */
    createForwardedMessage(forwardedMessage: ForwardedMessage): Observable<EntityResponseType> {
        const dto = forwardedMessage.toDTO();

        return this.http.post<ForwardedMessageDTO>(`${this.resourceUrl}`, dto, { observe: 'response' }).pipe(
            catchError(() => {
                return throwError(() => new Error('Failed to create forwarded message'));
            }),
        );
    }

    /**
     * Retrieves forwarded messages for a given set of IDs and message type.
     *
     * @param postingIds - An array of numeric IDs for which forwarded messages should be retrieved.
     * @param type - The type of messages to retrieve (PostingType.POST or PostingType.ANSWER).
     * @returns An observable containing a list of objects where each object includes an ID and its corresponding messages (as DTOs), wrapped in an HttpResponse.
     */
    getForwardedMessages(postingIds: number[], type: PostingType): Observable<HttpResponse<{ id: number; messages: ForwardedMessageDTO[] }[]>> {
        if (!postingIds || postingIds.length === 0) {
            return throwError(() => new Error('IDs cannot be empty'));
        }
        const params = new HttpParams().set('postingIds', postingIds.join(',')).set('type', type.toString());

        return this.http
            .get<{ id: number; messages: ForwardedMessageDTO[] }[]>(this.resourceUrl, {
                params,
                observe: 'response',
            })
            .pipe(
                catchError(() => {
                    return throwError(() => new Error('Failed to retrieve forwarded messages'));
                }),
            );
    }
}

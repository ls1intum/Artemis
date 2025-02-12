import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { ForwardedMessage, ForwardedMessageDTO } from 'app/entities/metis/forwarded-message.model';
import { PostingType } from 'app/entities/metis/posting.model';

type EntityResponseType = HttpResponse<ForwardedMessageDTO>;

@Injectable({ providedIn: 'root' })
export class ForwardedMessageService {
    public resourceUrl = 'api/communication/forwarded-messages';

    private http = inject(HttpClient);

    createForwardedMessage(forwardedMessage: ForwardedMessage, courseId: number): Observable<EntityResponseType> {
        const dto: ForwardedMessageDTO = forwardedMessage.toDTO();

        let params = new HttpParams();
        if (courseId) {
            params = params.set('courseId', courseId.toString());
        }

        return this.http.post<ForwardedMessageDTO>(`${this.resourceUrl}`, dto, {
            params,
            observe: 'response',
        });
    }

    /**
     * Retrieves forwarded messages for a given set of IDs and message type.
     *
     * @param postingIds - An array of numeric IDs for which forwarded messages should be retrieved.
     * @param type - The type of messages to retrieve (PostingType.POST or PostingType.ANSWER).
     * @returns An observable containing a list of objects where each object includes an ID and its corresponding messages (as DTOs), wrapped in an HttpResponse.
     */
    getForwardedMessages(postingIds: number[], type: PostingType, courseId: number): Observable<HttpResponse<{ id: number; messages: ForwardedMessageDTO[] }[]>> {
        if (!postingIds || postingIds.length === 0) {
            return throwError(() => new Error('IDs cannot be empty'));
        }
        const typeKey = PostingType[type];
        const params = new HttpParams().set('postingIds', postingIds.join(',')).set('type', typeKey).set('courseId', courseId.toString());

        return this.http.get<{ id: number; messages: ForwardedMessageDTO[] }[]>(this.resourceUrl, {
            params,
            observe: 'response',
        });
    }
}

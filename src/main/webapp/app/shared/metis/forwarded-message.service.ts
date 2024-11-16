import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ForwardedMessage } from 'app/entities/metis/forwarded-message.model';

type EntityResponseType = HttpResponse<ForwardedMessage>;
type EntityArrayResponseType = HttpResponse<ForwardedMessage[]>;

@Injectable({ providedIn: 'root' })
export class ForwardedMessageService {
    public resourceUrl = 'api/forwarded-messages';

    constructor(protected http: HttpClient) {}

    /**
     * Creates a new ForwardedMessage.
     * @param courseId The ID of the course.
     * @param forwardedMessage The ForwardedMessage to create.
     * @returns The created ForwardedMessage.
     */
    createForwardedMessage(forwardedMessage: ForwardedMessage): Observable<EntityResponseType> {
        const copy = this.convertForwardedMessage(forwardedMessage);
        return this.http.post<ForwardedMessage>(`${this.resourceUrl}`, copy, { observe: 'response' }).pipe(map((res: HttpResponse<ForwardedMessage>) => this.convertResponse(res)));
    }

    /**
     * Deletes a ForwardedMessage by ID.
     * @param id The ID of the ForwardedMessage to delete.
     * @returns An Observable of the HTTP response.
     */
    deleteForwardedMessage(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    /**
     * Finds all ForwardedMessages by Destination Post ID.
     * @param destinationPostId The Destination Post ID.
     * @returns A set of ForwardedMessages.
     */
    findAllByDestinationPostId(destinationPostId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<ForwardedMessage[]>(`${this.resourceUrl}/post/${destinationPostId}`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ForwardedMessage[]>) => this.convertArrayResponse(res)));
    }

    /**
     * Finds all ForwardedMessages by Destination Answer Post ID.
     * @param destinationAnswerId The Destination Answer Post ID.
     * @returns A set of ForwardedMessages.
     */
    findAllByDestinationAnswerId(destinationAnswerId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<ForwardedMessage[]>(`${this.resourceUrl}/answer/${destinationAnswerId}`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ForwardedMessage[]>) => this.convertArrayResponse(res)));
    }

    /**
     * Deletes all ForwardedMessages by Source ID and Source Type.
     * @param sourceId The Source ID.
     * @param sourceType The Source Type.
     * @returns An Observable of the HTTP response.
     */
    deleteAllBySourceIdAndSourceType(sourceId: number, sourceType: string): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/source/${sourceId}/type/${sourceType}`, { observe: 'response' });
    }

    private convertResponse(res: HttpResponse<ForwardedMessage>): HttpResponse<ForwardedMessage> {
        const body: ForwardedMessage = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<ForwardedMessage[]>): HttpResponse<ForwardedMessage[]> {
        const jsonResponse: ForwardedMessage[] = res.body!;
        const body: ForwardedMessage[] = jsonResponse.map(this.convertItemFromServer);
        return res.clone({ body });
    }

    private convertItemFromServer(forwardedMessage: ForwardedMessage): ForwardedMessage {
        return Object.assign({}, forwardedMessage);
    }

    private convertForwardedMessage(forwardedMessage: ForwardedMessage): ForwardedMessage {
        return Object.assign({}, forwardedMessage);
    }
}

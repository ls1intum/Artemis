import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { IrisClientMessageDescriptor, IrisMessageDescriptor } from 'app/entities/iris/iris.model';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { ActionType, MessageStoreAction } from 'app/iris/message-store.model';

type EntityResponseType = HttpResponse<IrisMessageDescriptor>;
type EntityArrayResponseType = HttpResponse<IrisMessageDescriptor[]>;

@Injectable()
export class IrisHttpMessageService implements OnDestroy {
    public resourceUrl = SERVER_API_URL + 'api/iris/sessions';
    // TODO @Dmytro Polityka set the number properly
    private readonly sessionId: number;
    private readonly subscription: Subscription;

    constructor(private httpClient: HttpClient, private messageStore: IrisMessageStore) {
        this.messageStore.getActionObservable().subscribe((newAction: MessageStoreAction) => {
            if (newAction.type !== ActionType.STUDENT_MESSAGE_SENT) return;
            this.create(this.sessionId, newAction.message).subscribe((response) => {
                if (response.body == null) throw Error('Server response does not contain proper values.');
                newAction.message.messageId = response.body.messageId;
            });
        });
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
    }

    /**
     * creates a message for a session
     * @param {number} sessionId
     * @param {IrisClientMessageDescriptor} message
     * @return {Observable<EntityResponseType>}
     */
    create(sessionId: number, message: IrisMessageDescriptor): Observable<EntityResponseType> {
        return this.httpClient.post<IrisClientMessageDescriptor>(`${this.resourceUrl}/${sessionId}/messages`, message, { observe: 'response' });
    }

    /**
     * gets all messages for a session by its id
     * @param {number} sessionId
     * @return {Observable<EntityArrayResponseType>}
     */
    getMessages(sessionId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<IrisMessageDescriptor[]>(`${this.resourceUrl}${sessionId}/messages`, { observe: 'response' });
    }
}

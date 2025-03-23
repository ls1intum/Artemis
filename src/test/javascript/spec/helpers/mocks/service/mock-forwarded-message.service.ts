import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ForwardedMessage } from 'app/communication/entities/forwarded-message.model';

export class MockForwardedMessageService {
    private forwardedMessages: Map<number, ForwardedMessage[]> = new Map();

    createForwardedMessage(forwardedMessage: ForwardedMessage): Observable<HttpResponse<ForwardedMessage>> {
        const destinationPostId = forwardedMessage.destinationPost?.id!;
        const messagesForPost = this.forwardedMessages.get(destinationPostId) || [];
        messagesForPost.push(forwardedMessage);
        this.forwardedMessages.set(destinationPostId, messagesForPost);

        return of(new HttpResponse({ body: forwardedMessage }));
    }

    getForwardedMessages(postIds: number[]): Observable<HttpResponse<Map<number, ForwardedMessage[]>>> {
        const result = new Map<number, ForwardedMessage[]>();
        postIds.forEach((postId) => {
            if (this.forwardedMessages.has(postId)) {
                result.set(postId, this.forwardedMessages.get(postId)!);
            }
        });

        return of(new HttpResponse({ body: result }));
    }
}

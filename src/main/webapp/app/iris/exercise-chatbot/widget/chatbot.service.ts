import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class ChatbotService {
    private displayChatSubject = new Subject<void>();

    displayChatObservable = this.displayChatSubject.asObservable();

    displayChat() {
        this.displayChatSubject.next();
    }
}

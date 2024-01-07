import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class ChatbotService {
    private displayChatSubject = new Subject<void>();

    displayChat$ = this.displayChatSubject.asObservable();

    displayChat() {
        this.displayChatSubject.next();
    }
}

import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class SharedService {
    private chatOpenSource = new BehaviorSubject<boolean>(false);
    chatOpen = this.chatOpenSource.asObservable();

    changeChatOpenStatus(status: boolean) {
        this.chatOpenSource.next(status);
    }
}

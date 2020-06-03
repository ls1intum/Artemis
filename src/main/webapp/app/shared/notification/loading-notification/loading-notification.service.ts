import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class LoadingNotificationService {
    isLoading = false;
    loadingStatus: Subject<boolean> = new Subject();

    startLoading() {
        this.isLoading = true;
        this.loadingStatus.next(true);
    }

    stopLoading() {
        this.isLoading = false;
        this.loadingStatus.next(false);
    }
}

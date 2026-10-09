import { Service } from '@angular/core';
import { Subject } from 'rxjs';

@Service()
export class LoadingNotificationService {
    loadingStatus: Subject<boolean> = new Subject();

    /**
     * Emit value to display loading screen
     */
    startLoading() {
        this.loadingStatus.next(true);
    }

    /**
     * Emit value to close loading screen
     */
    stopLoading() {
        this.loadingStatus.next(false);
    }
}

import { Observable, Subject } from 'rxjs';
import type { OnboardingResult } from './iris-onboarding.service';

/** Handle shared by the onboarding tour and its opener: the tour closes itself through it, the opener waits on `onClose`. */
export class IrisOnboardingRef {
    private readonly closeEvents = new Subject<OnboardingResult | undefined>();
    private closed = false;

    /** Emits once, with the result the tour closed with, and then completes. */
    readonly onClose: Observable<OnboardingResult | undefined> = this.closeEvents.asObservable();

    close(result?: OnboardingResult): void {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.closeEvents.next(result);
        this.closeEvents.complete();
    }
}

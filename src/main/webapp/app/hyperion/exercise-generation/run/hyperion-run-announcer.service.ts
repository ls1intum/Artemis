import { Injectable, inject } from '@angular/core';
import { LiveAnnouncer } from '@angular/cdk/a11y';

/**
 * The run page's one live region, for the three moments a screen-reader user must be told about without going looking.
 *
 * A run takes ten to twenty-five minutes, so the user who started it is not watching. WCAG 4.1.3 asks that a waiting
 * state, its progress and its errors be programmatically determinable without moving focus, and an in-template live
 * region cannot do it here: the regions that carry the answer are inside `@if` blocks that only become true in the
 * same change-detection pass that first fills them, so their first announcement is missed. The CDK announcer owns a
 * region mounted for the lifetime of the application, which is the only shape that survives that.
 *
 * Exactly three triggers, and nothing else: a stage change, entering the stalled state, and the terminal transition.
 * No clock, no counter, no per-file event - `role="timer"` has implicit `aria-live="off"` for precisely the reason
 * that a value updating once a second is not a status message.
 *
 * De-duplicated on an identity the caller supplies rather than on the rendered text, because the stalled announcement
 * has to be made once on entering the state - not again every time the duration inside its sentence ticks over.
 */
@Injectable()
export class HyperionRunAnnouncerService {
    private readonly announcer = inject(LiveAnnouncer);
    private lastId = '';

    /** Announces politely, and only when this is a different state from the one already announced. */
    announce(id: string, message: string): void {
        if (!id || !message || id === this.lastId) {
            return;
        }
        this.lastId = id;
        void this.announcer.announce(message, 'polite');
    }
}

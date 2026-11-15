import { Injectable, inject } from '@angular/core';
import { LiveAnnouncer } from '@angular/cdk/a11y';

/**
 * Announces stage, stall, and terminal transitions through CDK's persistent live region.
 * Deduplicates by state identity so clock updates do not repeat an announcement.
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

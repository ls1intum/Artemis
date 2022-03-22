import { Event, EventProcessor, Hub, Integration, StackFrame } from '@sentry/types';
import { sha1Hex } from 'app/shared/util/crypto.utils';

export class ArtemisDeduplicate implements Integration {
    public static id = 'ArtemisDeduplicate';
    public name: string = ArtemisDeduplicate.id;

    private observedEventHashes: string[] = [];

    public setupOnce(addGlobalEventProcessor: (callback: EventProcessor) => void, getCurrentHub: () => Hub): void {
        addGlobalEventProcessor((currentEvent: Event) => {
            const self = getCurrentHub().getIntegration(ArtemisDeduplicate);
            if (self) {
                // Try-catch to make sure that we submit the event if something goes wrong during deduplication
                try {
                    // Hash the current event and check if hash is present in already seen event hashes
                    const eventHash = computeEventHash(currentEvent);
                    if (this.observedEventHashes.includes(eventHash)) {
                        return null; // Drop event
                    } else {
                        // Add event to seen events and schedule removal in 5 minutes (throttle)
                        this.observedEventHashes.push(eventHash);
                        setTimeout(() => {
                            const index = this.observedEventHashes.indexOf(eventHash);
                            if (index >= 0) {
                                this.observedEventHashes.splice(index, 1);
                            }
                        }, 5 * 60 * 1000);
                    }
                } catch (e) {
                    console.error(e);
                }
            }
            return currentEvent;
        });
    }
}

/**
 * Sticks together a simple string with important values of the event and hashes it to produce an easily comparable value
 * @param event the sentry event to hash
 */
function computeEventHash(event: Event): string {
    // Add the message
    let valueSequence = event.message ?? '';

    // If the event has an exception, add type, value and for each frame, filename and line
    const exception = event.exception?.values && event.exception!.values.length > 0 && event.exception!.values[0];
    if (exception) {
        valueSequence += exception.type ?? '';
        valueSequence += exception.value ?? '';

        const frames = getFramesFromEvent(event);
        if (frames) {
            frames.forEach((frame) => (valueSequence += frame.filename ?? ''));
            frames.forEach((frame) => (valueSequence += frame.lineno ?? ''));
        }
    }

    return sha1Hex(valueSequence);
}

/**
 * Return the stack trace frames of the event exception or the event itself
 * @param event the event to get frames from
 */
function getFramesFromEvent(event: Event): StackFrame[] | undefined {
    const exception = event.exception;

    if (exception) {
        try {
            // @ts-ignore Object is possibly undefined
            return exception.values[0].stacktrace?.frames;
        } catch (_oO) {
            return undefined;
        }
    } else if (event.stacktrace) {
        return event.stacktrace.frames;
    }
    return undefined;
}

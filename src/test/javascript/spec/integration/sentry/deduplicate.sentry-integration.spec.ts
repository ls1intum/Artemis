import { ArtemisDeduplicate } from 'app/core/sentry/deduplicate.sentry-integration';
import { Event } from '@sentry/types';
import dayjs from 'dayjs/esm';
import { fakeAsync, flush, tick } from '@angular/core/testing';

describe('ArtemisDeduplicateSentryIntegration', () => {
    let dedup: ArtemisDeduplicate;
    let globalEventProcessor: (currentEvent: Event) => Event | null;

    beforeEach(() => {
        dedup = new ArtemisDeduplicate();
        dedup.setupOnce(
            (callback) => {
                // @ts-ignore
                globalEventProcessor = callback;
            },
            // @ts-ignore
            () => ({ getIntegration: () => dedup }),
        );
    });

    it.each([
        // Test exceptions
        (value: string) =>
            ({
                level: 'error',
                timestamp: dayjs().valueOf(),
                exception: {
                    values: [
                        {
                            type: 'Error',
                            value,
                            stacktrace: {
                                frames: [
                                    {
                                        filename: 'a.ts',
                                        lineno: 5,
                                    },
                                    {
                                        filename: 'b.ts',
                                        lineno: 10,
                                    },
                                ],
                            },
                        },
                    ],
                },
            }) as any as Event,

        // Test messages (no exception, but stacktrace)
        (value: string) =>
            ({
                level: 'error',
                timestamp: dayjs().valueOf(),
                message: value,
                stacktrace: {
                    frames: [
                        {
                            filename: 'a.ts',
                            lineno: 5,
                        },
                        {
                            filename: 'b.ts',
                            lineno: 10,
                        },
                    ],
                },
            }) as any as Event,

        // Test messages (no exception, no stacktrace)
        (value: string) =>
            ({
                level: 'error',
                timestamp: dayjs().valueOf(),
                message: value,
            }) as any as Event,
    ])(
        'should ignore exceptions and error messages that happened already in the last 5 minutes',
        fakeAsync((createEvent: (value: string) => Event) => {
            const ev0 = createEvent('test0');
            expect(globalEventProcessor(ev0)).toBe(ev0);

            // Identical event should be ignored
            const ev1 = createEvent('test0');
            expect(globalEventProcessor(ev1)).toBeNull();

            // An event with different value should go through
            const ev2 = createEvent('test1');
            expect(globalEventProcessor(ev2)).toBe(ev2);

            tick(5 * 60 * 1000);

            // Original event should work again after 5 minutes
            const ev3 = createEvent('test0');
            expect(globalEventProcessor(ev3)).toBe(ev3);

            flush();
        }),
    );
});

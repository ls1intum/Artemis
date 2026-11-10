import { Page, expect } from '@playwright/test';

/**
 * Records the STOMP destinations a page subscribes to, so a test can wait until the page listens before it triggers a live update.
 *
 * A websocket message is not stored for late subscribers. If the test sends its request while the page's websocket is still connecting
 * (or before its service subscribed), the update is published to nobody and the page never shows it. On a multi-node setup with a broker
 * relay the connect and subscribe round trips are slower, which turns this race into a frequent failure.
 *
 * Call this before the navigation that opens the websocket: a full page load creates a new connection, and a SUBSCRIBE sent earlier is not replayed.
 */
export function trackWebsocketSubscriptions(page: Page) {
    const destinations = new Set<string>();
    page.on('websocket', (socket) => {
        socket.on('framesent', ({ payload }) => {
            const frame = payload.toString();
            if (frame.startsWith('SUBSCRIBE')) {
                const destination = /^destination:(.*)$/m.exec(frame)?.[1]?.trim();
                if (destination) {
                    destinations.add(destination);
                }
            }
        });
    });
    return {
        /** Resolves once the page has sent a SUBSCRIBE frame whose destination contains the given text. */
        async waitFor(destinationPart: string) {
            await expect
                .poll(() => [...destinations].some((destination) => destination.includes(destinationPart)), { message: `websocket subscription to ${destinationPart}` })
                .toBe(true);
        },
    };
}

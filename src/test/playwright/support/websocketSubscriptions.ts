import { Page, expect } from '@playwright/test';

/**
 * Spring's user registry shares the sessions of a node with the other nodes only every 10 seconds. Until then a node that sends a message to a user
 * (a "/user/topic/..." destination) knows the user's older sessions but not the new one, and the message is not delivered to the new page. This
 * matters on a setup with several nodes whenever a page connected shortly before the update, and the E2E users are shared by parallel tests, so they
 * have older sessions on other nodes.
 */
const USER_REGISTRY_BROADCAST_INTERVAL_MS = 10_000;
const USER_REGISTRY_MARGIN_MS = 1_000;

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
    let connectedAt: number | undefined;
    page.on('websocket', (socket) => {
        socket.on('framereceived', ({ payload }) => {
            if (payload.toString().startsWith('CONNECTED')) {
                connectedAt = Date.now();
            }
        });
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
        /**
         * Resolves once every node knows the page's websocket session, so that a message addressed to the user reaches it whichever node sends it.
         * Waits only for the part of the registry interval that has not passed since the page connected.
         */
        async waitUntilKnownToAllNodes() {
            await expect.poll(() => connectedAt, { message: 'websocket connection of the page' }).toBeDefined();
            const remaining = connectedAt! + USER_REGISTRY_BROADCAST_INTERVAL_MS + USER_REGISTRY_MARGIN_MS - Date.now();
            if (remaining > 0) {
                await page.waitForTimeout(remaining);
            }
        },
    };
}

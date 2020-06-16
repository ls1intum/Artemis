// Service worker
//
// References
// https://github.com/webmaxru/pwatter/blob/workbox/src/sw-default.js
// Caching strategies: https://developers.google.com/web/tools/workbox/modules/workbox-strategies#stale-while-revalidate
// Example: https://github.com/JeremieLitzler/mws.nd.2018.s3/blob/master/sw.js

import { CacheableResponsePlugin } from 'workbox-cacheable-response';
import { ExpirationPlugin } from 'workbox-expiration';
import { precacheAndRoute, cleanupOutdatedCaches, createHandlerBoundToURL } from 'workbox-precaching';
import { registerRoute, NavigationRoute } from 'workbox-routing';
import { skipWaiting, clientsClaim } from 'workbox-core';
import { NetworkFirst, NetworkOnly, StaleWhileRevalidate, CacheFirst } from 'workbox-strategies';

declare const self: any;

const componentName = 'Service Worker';

// Enable debug mode during development
const DEBUG_MODE = location.hostname.endsWith('.app.local') || location.hostname === 'localhost';

const DAY_IN_SECONDS = 24 * 60 * 60;
const MONTH_IN_SECONDS = DAY_IN_SECONDS * 30;
const YEAR_IN_SECONDS = DAY_IN_SECONDS * 365;

/**
 * The current version of the service worker.
 */
const SERVICE_WORKER_VERSION = '1.0.5';

if (DEBUG_MODE) {
    // eslint-disable-next-line no-console
    // tslint:disable-next-line no-console
    console.debug(`Service worker version ${SERVICE_WORKER_VERSION} loading...`);
}

// -------------------------------------------------------------
// Precaching configuration
// -------------------------------------------------------------
cleanupOutdatedCaches();

// Precaching
// Make sure that all the assets passed in the array below are fetched and cached
// The empty array below is replaced at build time with the full list of assets to cache
// This is done by workbox-build-inject.js for the production build
const assetsToCache: { revision: string; url: string }[] = self.__WB_MANIFEST;
// To customize the assets afterwards:
// assetsToCache = [...assetsToCache, ???];

if (DEBUG_MODE) {
    // eslint-disable-next-line no-console
    // tslint:disable-next-line no-console
    console.trace(`${componentName}:: Assets that will be cached: `, assetsToCache);
}

// TODO: evaluate if needed -> relevant for offline first
skipWaiting();
clientsClaim();

// precaching of assets
precacheAndRoute(assetsToCache);

// -------------------------------------------------------------
// Routes
// -------------------------------------------------------------
// Default page handler for offline usage,
// where the browser does not how to handle deep links
// it's a SPA, so each path that is a navigation should default to index.html
const defaultRouteHandler = createHandlerBoundToURL('/index.html');
const defaultNavigationRoute = new NavigationRoute(defaultRouteHandler, {
    // allowlist: [],
    // denylist: [],
});
registerRoute(defaultNavigationRoute);

// Cache the Google Fonts stylesheets with a stale while revalidate strategy.
registerRoute(
    /^https:\/\/fonts\.googleapis\.com/,
    new StaleWhileRevalidate({
        cacheName: 'google-fonts-stylesheets',
    }),
);

// Cache the Google Fonts webfont files with a cache first strategy for 1 year.
registerRoute(
    /^https:\/\/fonts\.gstatic\.com/,
    new CacheFirst({
        cacheName: 'google-fonts-webfonts',
        plugins: [
            new CacheableResponsePlugin({
                statuses: [0, 200],
            }),
            new ExpirationPlugin({
                maxAgeSeconds: YEAR_IN_SECONDS,
                maxEntries: 30,
                purgeOnQuotaError: true, // Automatically cleanup if quota is exceeded.
            }),
        ],
    }),
);

// Make JS/CSS fast by returning assets from the cache
// But make sure they're updating in the background for next use
registerRoute(/\.(?:js|css)$/, new StaleWhileRevalidate());
registerRoute(/\.(?:json)\?buildTimestamp.*$/, new StaleWhileRevalidate());

registerRoute(/(https:\/\/)?([^\/\s]+\/)api\/.*/, new StaleWhileRevalidate());

// Cache images
// But clean up after a while
registerRoute(
    /\.(?:png|gif|jpg|jpeg|svg)$/,
    new CacheFirst({
        cacheName: 'images',
        plugins: [
            new ExpirationPlugin({
                maxEntries: 250,
                maxAgeSeconds: MONTH_IN_SECONDS,
                purgeOnQuotaError: true, // Automatically cleanup if quota is exceeded.
            }),
        ],
    }),
);

// Anything authentication related MUST be performed online
registerRoute(/(https:\/\/)?([^\/\s]+\/)api\/authenticate\/.*/, new NetworkFirst());

// Database access is only supported while online
// registerRoute(/(https:\/\/)?([^\/\s]+\/)database\/.*/, new NetworkOnly());

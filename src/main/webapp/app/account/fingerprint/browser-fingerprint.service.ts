import { Service, inject } from '@angular/core';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { BehaviorSubject } from 'rxjs';
import type { GetResult } from '@fingerprintjs/fingerprintjs';
import { generateUuid } from 'app/foundation/util/crypto.utils';

/** How long a request that needs the fingerprint waits for it, so that a slow or blocked library never holds a request back. */
const FINGERPRINT_WAIT_MILLISECONDS = 3000;

interface IdleWindow {
    requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => void;
}

/**
 * Service for generating and managing browser fingerprints and instance identifiers.
 * The server records them with an exam session, to recognize the browser that takes an exam.
 *
 * The fingerprint comes from a library that is loaded on demand: {@link startWhenIdle} starts it for a signed-in user once the browser is idle,
 * and {@link ensureFingerprint} starts it at once and waits for it, for the request that creates an exam session. A visitor of the sign-in page
 * never loads it.
 *
 * - Browser Fingerprint: A hash generated from browser characteristics (screen size, plugins, etc.)
 *   that remains relatively stable across sessions on the same browser.
 * - Instance Identifier: A UUID stored in local storage to identify this specific browser instance.
 * - Session Identifier: A UUID stored in session storage to identify the current browser tab.
 */
@Service()
export class BrowserFingerprintService {
    private localStorageService = inject(LocalStorageService);
    private sessionStorageService = inject(SessionStorageService);

    /** Local storage key for persisting the browser instance identifier */
    private readonly INSTANCE_STORAGE_KEY = 'instanceIdentifier';
    /** Session storage key for persisting the browser session identifier */
    private readonly SESSION_STORAGE_KEY = 'sessionIdentifier';

    /** Whether the server wants fingerprints: set by {@link initialize}, and false until then. */
    private fingerprintsEnabled = false;
    /** The generation of the fingerprint, once it has started. */
    private fingerprint?: Promise<string | undefined>;
    private idleStartScheduled = false;

    /**
     * Observable containing the browser fingerprint hash.
     * Generated using FingerprintJS library based on browser characteristics.
     */
    public browserFingerprint = new BehaviorSubject<string | undefined>(undefined);

    /**
     * Observable containing the unique instance identifier for this browser.
     * Persisted in local storage across sessions.
     */
    public browserInstanceId = new BehaviorSubject<string | undefined>(undefined);

    /**
     * Observable containing the unique session identifier for this browser tab.
     * Stored in session storage, which is scoped per tab, so it resets when the
     * tab is closed and differs across tabs.
     */
    public browserSessionId = new BehaviorSubject<string | undefined>(undefined);

    /**
     * Initializes the fingerprint service based on server configuration.
     * If fingerprints are disabled, clears any stored instance data.
     *
     * @param browserFingerprintsEnabled - Whether fingerprinting is enabled. Defaults to true if undefined
     *                                      to maintain backwards compatibility with older configurations.
     */
    public initialize(browserFingerprintsEnabled: boolean | undefined): void {
        // Session identifier is always needed for message routing (e.g., self-message filtering
        // in collaborative editing) and is independent of browser fingerprinting.
        this.initializeSessionIdentifier();

        // Default to enabled for backwards compatibility with older configurations
        this.fingerprintsEnabled = browserFingerprintsEnabled !== false;
        if (this.fingerprintsEnabled) {
            this.initializeInstanceIdentifier();
        } else {
            this.clearStoredInstanceData();
        }
    }

    /**
     * Starts generating the browser fingerprint once the browser is idle. Call it when a user is signed in: the server reads the fingerprint
     * when an exam session starts, which is well after the sign-in, and an anonymous visitor does not need it. Safe to call repeatedly.
     */
    public startWhenIdle(): void {
        if (!this.fingerprintsEnabled || this.fingerprint || this.idleStartScheduled || typeof window === 'undefined') {
            return;
        }
        this.idleStartScheduled = true;
        const start = () => void this.generateBrowserFingerprint();
        const idleWindow = window as IdleWindow;
        if (idleWindow.requestIdleCallback) {
            idleWindow.requestIdleCallback(start, { timeout: 5000 });
        } else {
            window.setTimeout(start, 3000);
        }
    }

    /** Whether fingerprints are enabled and the fingerprint is not available yet, so that a request which has to carry it must wait for {@link ensureFingerprint}. */
    public isFingerprintPending(): boolean {
        return this.fingerprintsEnabled && this.browserFingerprint.value === undefined;
    }

    /**
     * Returns the fingerprint for a request that has to carry it, starting the generation if it has not started yet. The wait is bounded: when the
     * library is slow, blocked or fails to load, this resolves to `undefined` and the request goes without a fingerprint, as it did before.
     *
     * @returns the fingerprint, or `undefined` when fingerprints are disabled or it is not available in time
     */
    public ensureFingerprint(): Promise<string | undefined> {
        if (!this.fingerprintsEnabled) {
            return Promise.resolve(undefined);
        }
        let timer: ReturnType<typeof setTimeout> | undefined;
        const timeout = new Promise<undefined>((resolve) => {
            timer = setTimeout(() => resolve(undefined), FINGERPRINT_WAIT_MILLISECONDS);
        });
        return Promise.race([this.generateBrowserFingerprint(), timeout]).finally(() => clearTimeout(timer));
    }

    /**
     * Generates a browser fingerprint using FingerprintJS, once. The fingerprint is a hash based on browser characteristics like
     * screen resolution, installed fonts, plugins, and other attributes.
     */
    private generateBrowserFingerprint(): Promise<string | undefined> {
        // The library is imported when it is needed, not with the main bundle (see the class description for who needs it and when).
        this.fingerprint ??= import('@fingerprintjs/fingerprintjs')
            .then(({ default: FingerprintJS }) => FingerprintJS.load())
            .then((fingerprintAgent: { get: () => Promise<GetResult> }) => fingerprintAgent.get())
            .then((result) => {
                this.browserFingerprint.next(result.visitorId);
                return result.visitorId;
            })
            .catch(() => {
                // The library is a separate file, which can fail to load (a stale tab after a deployment, a content blocker, a bad connection).
                // The fingerprint then stays unset: requests go without it.
                return undefined;
            });
        return this.fingerprint;
    }

    /**
     * Initializes or retrieves the browser instance identifier.
     * If no identifier exists in local storage, generates a new UUID.
     * This identifier persists across browser sessions but is unique per browser/device.
     */
    private initializeInstanceIdentifier(): void {
        let storedInstanceId = this.localStorageService.retrieve<string>(this.INSTANCE_STORAGE_KEY);

        if (!storedInstanceId) {
            // Generate a new UUID for this browser instance
            storedInstanceId = generateUuid();
            this.localStorageService.store<string>(this.INSTANCE_STORAGE_KEY, storedInstanceId);
        }

        this.browserInstanceId.next(storedInstanceId);
    }

    /**
     * Initializes or retrieves the browser session identifier.
     * If no identifier exists in session storage, generates a new UUID.
     * This identifier persists for the lifetime of the tab.
     */
    private initializeSessionIdentifier(): void {
        let storedSessionId = this.sessionStorageService.retrieve<string>(this.SESSION_STORAGE_KEY);

        if (!storedSessionId) {
            storedSessionId = generateUuid();
            this.sessionStorageService.store<string>(this.SESSION_STORAGE_KEY, storedSessionId);
        }

        this.browserSessionId.next(storedSessionId);
    }

    /**
     * Removes the stored instance identifier from local storage.
     * Called when fingerprinting is disabled on the server.
     */
    private clearStoredInstanceData(): void {
        this.localStorageService.remove(this.INSTANCE_STORAGE_KEY);
    }
}

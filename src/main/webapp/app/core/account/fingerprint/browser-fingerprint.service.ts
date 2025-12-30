import { Injectable, inject } from '@angular/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { BehaviorSubject } from 'rxjs';
import FingerprintJS, { GetResult } from '@fingerprintjs/fingerprintjs';

/**
 * Service for generating and managing browser fingerprints and instance identifiers.
 * Used for security purposes such as detecting suspicious login patterns or
 * identifying the same browser across sessions.
 *
 * - Browser Fingerprint: A hash generated from browser characteristics (screen size, plugins, etc.)
 *   that remains relatively stable across sessions on the same browser.
 * - Instance Identifier: A UUID stored in local storage to identify this specific browser instance.
 */
@Injectable({ providedIn: 'root' })
export class BrowserFingerprintService {
    private localStorageService = inject(LocalStorageService);

    /** Local storage key for persisting the browser instance identifier */
    private readonly INSTANCE_STORAGE_KEY = 'instanceIdentifier';

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
     * Initializes the fingerprint service based on server configuration.
     * If fingerprints are disabled, clears any stored instance data.
     *
     * @param browserFingerprintsEnabled - Whether fingerprinting is enabled. Defaults to true if undefined
     *                                      to maintain backwards compatibility with older configurations.
     */
    public initialize(browserFingerprintsEnabled: boolean | undefined): void {
        // Default to enabled for backwards compatibility with older configurations
        if (browserFingerprintsEnabled !== false) {
            this.generateBrowserFingerprint();
            this.initializeInstanceIdentifier();
        } else {
            this.clearStoredInstanceData();
        }
    }

    /**
     * Generates a browser fingerprint using FingerprintJS.
     * The fingerprint is a hash based on browser characteristics like
     * screen resolution, installed fonts, plugins, and other attributes.
     */
    private generateBrowserFingerprint(): void {
        FingerprintJS.load().then((fingerprintAgent: { get: () => Promise<GetResult> }) => {
            fingerprintAgent.get().then((result) => {
                this.browserFingerprint.next(result.visitorId);
            });
        });
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
            storedInstanceId = window.crypto.randomUUID();
            this.localStorageService.store<string>(this.INSTANCE_STORAGE_KEY, storedInstanceId);
        }

        this.browserInstanceId.next(storedInstanceId);
    }

    /**
     * Removes the stored instance identifier from local storage.
     * Called when fingerprinting is disabled on the server.
     */
    private clearStoredInstanceData(): void {
        this.localStorageService.remove(this.INSTANCE_STORAGE_KEY);
    }
}

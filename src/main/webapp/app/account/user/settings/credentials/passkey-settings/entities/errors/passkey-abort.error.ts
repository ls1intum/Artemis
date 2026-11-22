/**
 * Used to identify abort signals initiated by the application (e.g., aborting a pending
 * conditional mediation request before starting a modal passkey login).
 * Distinguishes our own aborts from browser-initiated AbortErrors.
 */
export class PasskeyAbortError extends Error {
    constructor(message: string = 'The pending passkey credential request was aborted to allow a new request.') {
        super(message);
        this.name = 'PasskeyAbortError';
        Object.setPrototypeOf(this, PasskeyAbortError.prototype);
    }
}

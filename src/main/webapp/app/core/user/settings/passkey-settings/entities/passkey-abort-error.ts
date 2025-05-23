export class PasskeyAbortError extends Error {
    constructor(message: string = 'Cannot initiate a new credential request while a pending request is active. The previous request has been aborted to proceed.') {
        super(message);
        this.name = 'AbortError';
        Object.setPrototypeOf(this, PasskeyAbortError.prototype); // ensure instanceof checks works correctly
    }
}

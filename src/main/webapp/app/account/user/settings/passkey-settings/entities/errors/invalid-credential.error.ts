export class InvalidCredentialError extends Error {
    constructor(message: string = 'Invalid credential') {
        super(message);
        this.name = 'InvalidCredentialError';
        Object.setPrototypeOf(this, InvalidCredentialError.prototype); // ensure instanceof checks work correctly
    }
}

import { getCredentialWithGracefullyHandlingAuthenticatorIssues } from './credential.util';
import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';

describe('getCredentialWithGracefullyHandlingAuthenticatorIssues', () => {
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should return the credential if it can be stringified', () => {
        const validCredential = { id: '123' } as unknown as Credential;
        const result = getCredentialWithGracefullyHandlingAuthenticatorIssues(validCredential);
        expect(result).toEqual(validCredential);
    });

    it('should handle malformed Bitwarden credentials gracefully', () => {
        jest.spyOn(console, 'warn').mockImplementation(); // Suppress console warnings in the test
        const malformedCredential: MalformedBitwardenCredential = {
            id: 'mock-id',
            rawId: { 0: 1, 1: 2, 2: 3 },
            type: 'public-key',
            authenticatorAttachment: 'platform',
            response: {
                clientDataJSON: { 0: 123, 1: 34, 2: 116, 3: 101, 4: 115, 5: 116, 6: 34, 7: 125 },
                attestationObject: { 0: 10, 1: 20, 2: 30 },
                getAuthenticatorData: () => ({ 0: 40, 1: 50, 2: 60 }),
                getPublicKey: () => ({ 0: 70, 1: 80, 2: 90 }),
                getPublicKeyAlgorithm: () => -7,
                getTransports: () => ['usb', 'nfc'],
            },
            getClientExtensionResults: () => ({}),
        };
        // Define a custom toJSON method that throws an error (which is the bug in the Bitwarden plugin)
        (malformedCredential as any).toJSON = () => {
            throw new TypeError('Illegal invocation');
        };

        const result = getCredentialWithGracefullyHandlingAuthenticatorIssues(malformedCredential);
        expect(result).toEqual({
            authenticatorAttachment: 'platform',
            clientExtensionResults: {},
            id: 'mock-id',
            rawId: 'AQID',
            response: {
                attestationObject: 'ChQe',
                authenticatorData: 'KDI8',
                clientDataJSON: 'eyJ0ZXN0In0',
                publicKey: 'RlBa',
                publicKeyAlgorithm: -7,
                signature: undefined,
                transports: ['usb', 'nfc'],
                userHandle: undefined,
            },
            type: 'public-key',
        });
    });

    it('should handle malformed Bitwarden credentials gracefully during login', () => {
        jest.spyOn(console, 'warn').mockImplementation(); // Suppress console warnings in the test
        const malformedCredential: MalformedBitwardenCredential = {
            id: 'mock-id',
            rawId: { 0: 1, 1: 2, 2: 3 },
            type: 'public-key',
            authenticatorAttachment: 'platform',
            response: {
                clientDataJSON: { 0: 123, 1: 34, 2: 116, 3: 101, 4: 115, 5: 116, 6: 34, 7: 125 },
                authenticatorData: { 0: 10, 1: 20, 2: 30 },
                signature: { 0: 40, 1: 50, 2: 60 },
                userHandle: { 0: 70, 1: 80, 2: 90 },
            },
            getClientExtensionResults: () => ({}),
        };
        // Define a custom toJSON method that throws an error (which is the bug in the Bitwarden plugin)
        (malformedCredential as any).toJSON = () => {
            throw new TypeError('Illegal invocation');
        };

        const result = getCredentialWithGracefullyHandlingAuthenticatorIssues(malformedCredential);
        expect(result).toEqual({
            authenticatorAttachment: 'platform',
            clientExtensionResults: {},
            id: 'mock-id',
            rawId: 'AQID',
            response: {
                authenticatorData: 'ChQe',
                clientDataJSON: 'eyJ0ZXN0In0',
                signature: 'KDI8',
                userHandle: 'RlBa',
            },
            type: 'public-key',
        });
    });

    it('should return null if the credential is null', () => {
        const result = getCredentialWithGracefullyHandlingAuthenticatorIssues(null);
        expect(result).toBeNull();
    });

    it('should throw InvalidCredentialError if the credential cannot be processed', () => {
        jest.spyOn(console, 'warn').mockImplementation(); // Suppress console warnings in the test // Suppress console warnings in the test
        const malformedCredential = { id: 'mock-id' } as unknown as Credential;

        // Mock the toJSON method to simulate an error
        (malformedCredential as any).toJSON = () => {
            throw new TypeError('Illegal invocation');
        };

        expect(() => {
            getCredentialWithGracefullyHandlingAuthenticatorIssues(malformedCredential);
        }).toThrow(InvalidCredentialError);
    });
});

import { getCredentialWithGracefullyHandlingAuthenticatorIssues } from './credential.util';
import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';

describe('getCredentialWithGracefullyHandlingAuthenticatorIssues', () => {
    it('should return the credential if it can be stringified', () => {
        const validCredential = { id: '123' } as unknown as Credential;
        const result = getCredentialWithGracefullyHandlingAuthenticatorIssues(validCredential);
        expect(result).toEqual(validCredential);
    });

    it('should handle malformed Bitwarden credentials gracefully', () => {
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
                clientDataJSON: 'eyJ0ZXN0In0=',
                publicKey: 'RlBa',
                publicKeyAlgorithm: -7,
                signature: undefined,
                transports: ['usb', 'nfc'],
                userHandle: undefined,
            },
            type: 'public-key',
        });
    });

    it('should return null if the credential is null', () => {
        const result = getCredentialWithGracefullyHandlingAuthenticatorIssues(null);
        expect(result).toBeNull();
    });
});

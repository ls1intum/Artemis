import { getCredentialFromMalformedBitwardenObject } from './bitwarden.util';
import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';

describe('Bitwarden Util', () => {
    const malformedCredential: MalformedBitwardenCredential = {
        id: 'test-id',
        rawId: { 0: 116, 1: 101, 2: 115, 3: 116 },
        type: 'public-key',
        authenticatorAttachment: 'platform',
        response: {
            clientDataJSON: { 0: 123, 1: 34, 2: 116, 3: 101, 4: 115, 5: 116, 6: 34, 7: 125 },
            attestationObject: { 0: 1, 1: 2, 2: 3 },
            getAuthenticatorData: () => ({ 0: 4, 1: 5, 2: 6 }),
            getPublicKey: () => ({ 0: 7, 1: 8, 2: 9 }),
            getPublicKeyAlgorithm: () => -7,
            getTransports: () => ['usb', 'nfc'],
        },
        getClientExtensionResults: () => ({}),
    };

    it('should convert a malformed Bitwarden credential to a valid Credential object', () => {
        const credential = getCredentialFromMalformedBitwardenObject(malformedCredential) as unknown as any;

        expect(credential).toBeDefined();
        expect(credential?.id).toBe('test-id');
        expect(credential?.type).toBe('public-key');
        expect(credential?.rawId).toBeDefined();
        expect(credential?.response).toBeDefined();
        expect(credential?.response.attestationObject).toBeDefined();
        expect(credential?.response.authenticatorData).toBeDefined();
        expect(credential?.response.clientDataJSON).toBeDefined();
        expect(credential?.response.publicKey).toBeDefined();
        expect(credential?.response.publicKeyAlgorithm).toBe(-7);
        expect(credential?.response.transports).toEqual(['usb', 'nfc']);
    });

    it('should return null for a null input', () => {
        const credential = getCredentialFromMalformedBitwardenObject(null);
        expect(credential).toBeNull();
    });

    it('should return null for an invalid object', () => {
        const invalidCredential = null;

        const result = getCredentialFromMalformedBitwardenObject(invalidCredential as unknown as MalformedBitwardenCredential);

        expect(result).toBeNull();
    });
});

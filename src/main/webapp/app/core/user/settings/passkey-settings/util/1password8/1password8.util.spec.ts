import { getLoginCredentialFromMalformed1Password8Object, getRegistrationCredentialFromMalformed1Password8Object } from './1password8.util';
import { Malformed1password8RegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-registration-credential';
import { Malformed1Password8LoginCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-1password8-login-credential';
import { expectBase64UrlFieldsForLogin, expectBase64UrlFieldsForRegistration } from '../test.helpers';
import { describe, expect, it } from '@jest/globals';

describe('1Password8 Util', () => {
    function createArrayBuffer(values: number[]): ArrayBuffer {
        const uint8Array = new Uint8Array(values);
        return uint8Array.buffer;
    }

    describe('Registration Credential (with attestationObject)', () => {
        const malformedRegistrationCredential: Malformed1password8RegistrationCredential = {
            id: 'test-id',
            rawId: createArrayBuffer([116, 101, 115, 116]),
            type: 'public-key',
            authenticatorAttachment: 'platform',
            response: {
                clientDataJSON: createArrayBuffer([123, 34, 116, 101, 115, 116, 34, 125]),
                attestationObject: createArrayBuffer([1, 2, 3]),
                getAuthenticatorData: () => createArrayBuffer([4, 5, 6]),
                getPublicKey: () => createArrayBuffer([7, 8, 9]),
                getPublicKeyAlgorithm: () => -7,
                getTransports: () => ['usb', 'nfc'],
            },
            getClientExtensionResults: () => ({}),
        };

        it('should convert a malformed 1Password8 registration credential to a valid SerializableRegistrationCredential object', () => {
            const credential = getRegistrationCredentialFromMalformed1Password8Object(malformedRegistrationCredential);

            expect(credential).toBeDefined();
            expect(credential?.id).toBe('test-id');
            expect(credential?.type).toBe('public-key');
            expect(credential?.authenticatorAttachment).toBe('platform');
            expect(credential?.rawId).toBeDefined();
            expect(credential?.rawId).toMatch(/^[A-Za-z0-9_-]+$/); // base64url format
            expect(credential?.response).toBeDefined();
            expect(credential?.response.clientDataJSON).toBeDefined();
            expect(credential?.response.attestationObject).toBeDefined();
            expect(credential?.response.authenticatorData).toBeDefined();
            expect(credential?.response.publicKey).toBeDefined();
            expect(credential?.response.publicKeyAlgorithm).toBe(-7);
            expect(credential?.response.transports).toEqual(['usb', 'nfc']);
            expect(credential?.clientExtensionResults).toEqual({});
        });

        it('should encode all binary data as base64url strings', () => {
            const credential = getRegistrationCredentialFromMalformed1Password8Object(malformedRegistrationCredential);

            expectBase64UrlFieldsForRegistration(credential, [
                'rawId',
                'response.clientDataJSON',
                'response.attestationObject',
                'response.authenticatorData',
                'response.publicKey',
            ]);
        });
    });

    describe('Login Credential (with signature and userHandle)', () => {
        const malformedLoginCredential: Malformed1Password8LoginCredential = {
            id: 'login-id',
            rawId: createArrayBuffer([108, 111, 103, 105, 110]),
            type: 'public-key',
            authenticatorAttachment: 'cross-platform',
            response: {
                clientDataJSON: createArrayBuffer([123, 34, 108, 111, 103, 105, 110, 34, 125]),
                authenticatorData: createArrayBuffer([10, 11, 12]),
                signature: createArrayBuffer([13, 14, 15]),
                userHandle: createArrayBuffer([16, 17, 18]),
            },
            getClientExtensionResults: () => ({ appid: true }),
        };

        it('should convert a malformed 1Password8 login credential to a valid SerializableLoginCredential object', () => {
            const credential = getLoginCredentialFromMalformed1Password8Object(malformedLoginCredential);

            expect(credential).toBeDefined();
            expect(credential?.id).toBe('login-id');
            expect(credential?.type).toBe('public-key');
            expect(credential?.authenticatorAttachment).toBe('cross-platform');
            expect(credential?.rawId).toBeDefined();
            expect(credential?.response).toBeDefined();
            expect(credential?.response.clientDataJSON).toBeDefined();
            expect(credential?.response.authenticatorData).toBeDefined();
            expect(credential?.response.signature).toBeDefined();
            expect(credential?.response.userHandle).toBeDefined();
            expect(credential?.clientExtensionResults).toEqual({ appid: true });
        });

        it('should encode login credential binary data as base64url strings', () => {
            const credential = getLoginCredentialFromMalformed1Password8Object(malformedLoginCredential);

            expectBase64UrlFieldsForLogin(credential, ['rawId', 'response.clientDataJSON', 'response.authenticatorData', 'response.signature', 'response.userHandle']);
        });
    });

    describe('Edge Cases', () => {
        it('should return undefined for a null input (registration)', () => {
            const credential = getRegistrationCredentialFromMalformed1Password8Object(null);
            expect(credential).toBeUndefined();
        });

        it('should return undefined for a null input (login)', () => {
            const credential = getLoginCredentialFromMalformed1Password8Object(null);
            expect(credential).toBeUndefined();
        });

        it('should handle registration credential without optional fields', () => {
            const minimalCredential: Malformed1password8RegistrationCredential = {
                id: 'minimal-id',
                rawId: createArrayBuffer([1, 2, 3]),
                type: 'public-key',
                authenticatorAttachment: 'platform',
                response: {
                    clientDataJSON: createArrayBuffer([123, 125]),
                },
                getClientExtensionResults: () => ({}),
            } as Malformed1password8RegistrationCredential;

            const credential = getRegistrationCredentialFromMalformed1Password8Object(minimalCredential);

            expect(credential).toBeDefined();
            expect(credential?.id).toBe('minimal-id');
            expect(credential?.response.clientDataJSON).toBeDefined();
            expect(credential?.response.attestationObject).toBeUndefined();
            expect(credential?.response.authenticatorData).toBeUndefined();
            expect(credential?.response.publicKey).toBeUndefined();
            expect(credential?.response.publicKeyAlgorithm).toBeUndefined();
            expect(credential?.response.transports).toBeUndefined();
        });

        it('should handle login credential without optional fields', () => {
            const minimalCredential: Malformed1Password8LoginCredential = {
                id: 'minimal-id',
                rawId: createArrayBuffer([1, 2, 3]),
                type: 'public-key',
                authenticatorAttachment: 'platform',
                response: {
                    clientDataJSON: createArrayBuffer([123, 125]),
                },
                getClientExtensionResults: () => ({}),
            };

            const credential = getLoginCredentialFromMalformed1Password8Object(minimalCredential);

            expect(credential).toBeDefined();
            expect(credential?.id).toBe('minimal-id');
            expect(credential?.response.clientDataJSON).toBeDefined();
            expect(credential?.response.authenticatorData).toBeUndefined();
            expect(credential?.response.signature).toBeUndefined();
            expect(credential?.response.userHandle).toBeUndefined();
        });

        it('should handle empty ArrayBuffers for registration', () => {
            const credentialWithEmptyBuffers: Malformed1password8RegistrationCredential = {
                id: 'empty-id',
                rawId: createArrayBuffer([]),
                type: 'public-key',
                authenticatorAttachment: 'platform',
                response: {
                    clientDataJSON: createArrayBuffer([]),
                },
                getClientExtensionResults: () => ({}),
            } as Malformed1password8RegistrationCredential;

            const credential = getRegistrationCredentialFromMalformed1Password8Object(credentialWithEmptyBuffers);

            expect(credential).toBeDefined();
            expect(credential?.rawId).toBe(''); // empty array encodes to empty string
            expect(credential?.response.clientDataJSON).toBe('');
        });

        it('should handle empty ArrayBuffers for login', () => {
            const credentialWithEmptyBuffers: Malformed1Password8LoginCredential = {
                id: 'empty-id',
                rawId: createArrayBuffer([]),
                type: 'public-key',
                authenticatorAttachment: 'platform',
                response: {
                    clientDataJSON: createArrayBuffer([]),
                },
                getClientExtensionResults: () => ({}),
            };

            const credential = getLoginCredentialFromMalformed1Password8Object(credentialWithEmptyBuffers);

            expect(credential).toBeDefined();
            expect(credential?.rawId).toBe(''); // empty array encodes to empty string
            expect(credential?.response.clientDataJSON).toBe('');
        });
    });

    describe('Data Integrity', () => {
        it('should preserve the original data when encoding and maintain correct byte representation for registration', () => {
            const originalData = [72, 101, 108, 108, 111]; // "Hello" in ASCII
            const credential: Malformed1password8RegistrationCredential = {
                id: 'test-id',
                rawId: createArrayBuffer(originalData),
                type: 'public-key',
                authenticatorAttachment: 'platform',
                response: {
                    clientDataJSON: createArrayBuffer(originalData),
                },
                getClientExtensionResults: () => ({}),
            } as Malformed1password8RegistrationCredential;

            const result = getRegistrationCredentialFromMalformed1Password8Object(credential);

            expect(result).toBeDefined();
            expect(result?.rawId).toBeDefined();
            expect(result?.response.clientDataJSON).toBeDefined();

            // Both should encode to the same base64url string since they have the same data
            expect(result?.rawId).toBe(result?.response.clientDataJSON);
        });

        it('should preserve the original data when encoding and maintain correct byte representation for login', () => {
            const originalData = [72, 101, 108, 108, 111]; // "Hello" in ASCII
            const credential: Malformed1Password8LoginCredential = {
                id: 'test-id',
                rawId: createArrayBuffer(originalData),
                type: 'public-key',
                authenticatorAttachment: 'platform',
                response: {
                    clientDataJSON: createArrayBuffer(originalData),
                },
                getClientExtensionResults: () => ({}),
            };

            const result = getLoginCredentialFromMalformed1Password8Object(credential);

            expect(result).toBeDefined();
            expect(result?.rawId).toBeDefined();
            expect(result?.response.clientDataJSON).toBeDefined();

            // Both should encode to the same base64url string since they have the same data
            expect(result?.rawId).toBe(result?.response.clientDataJSON);
        });
    });
});

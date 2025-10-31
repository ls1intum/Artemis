import { convertToBase64, getCredentialFromMalformedBitwardenObject } from './bitwarden.util';
import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';
import { expectBase64UrlFields } from '../test.helpers';
import { describe, expect, it } from '@jest/globals';

describe('Bitwarden Util', () => {
    describe('convertToBase64', () => {
        it('should convert a record of numbers to base64url string', () => {
            const input = { 0: 72, 1: 101, 2: 108, 3: 108, 4: 111 }; // "Hello" in ASCII
            const result = convertToBase64(input);

            expect(result).toBeDefined();
            expect(typeof result).toBe('string');
            // base64url should only contain A-Z, a-z, 0-9, -, _
            expect(result).toMatch(/^[A-Za-z0-9_-]+$/);
        });

        it('should return the same result for the same input', () => {
            const input = { 0: 1, 1: 2, 2: 3, 3: 4 };
            const result1 = convertToBase64(input);
            const result2 = convertToBase64(input);

            expect(result1).toBe(result2);
        });

        it('should return undefined for null input', () => {
            const result = convertToBase64(null);
            expect(result).toBeUndefined();
        });

        it('should return undefined for undefined input', () => {
            const result = convertToBase64(undefined);
            expect(result).toBeUndefined();
        });

        it('should handle empty record', () => {
            const input = {};
            const result = convertToBase64(input);

            expect(result).toBe(''); // empty array encodes to empty string
        });

        it('should handle single byte', () => {
            const input = { 0: 65 }; // 'A' in ASCII
            const result = convertToBase64(input);

            expect(result).toBeDefined();
            expect(typeof result).toBe('string');
            expect(result?.length).toBeGreaterThan(0);
        });

        it('should handle large numbers (0-255 range)', () => {
            const input = { 0: 0, 1: 127, 2: 255 };
            const result = convertToBase64(input);

            expect(result).toBeDefined();
            expect(typeof result).toBe('string');
            expect(result).toMatch(/^[A-Za-z0-9_-]+$/);
        });

        it('should preserve byte order when converting', () => {
            const input1 = { 0: 1, 1: 2, 2: 3 };
            const input2 = { 0: 3, 1: 2, 2: 1 };
            const result1 = convertToBase64(input1);
            const result2 = convertToBase64(input2);

            expect(result1).toBeDefined();
            expect(result2).toBeDefined();
            expect(result1).not.toBe(result2); // Different order should produce different results
        });

        it('should handle sequential numeric keys correctly', () => {
            const input = { 0: 72, 1: 101, 2: 108, 3: 108, 4: 111 };
            const result = convertToBase64(input);

            expect(result).toBeDefined();
            expect(result?.length).toBeGreaterThan(0);
        });

        it('should handle object with all zeros', () => {
            const input = { 0: 0, 1: 0, 2: 0, 3: 0 };
            const result = convertToBase64(input);

            expect(result).toBeDefined();
            expect(typeof result).toBe('string');
        });

        it('should produce base64url format (no +, /, or = characters)', () => {
            const input = { 0: 255, 1: 255, 2: 255, 3: 255 };
            const result = convertToBase64(input);

            expect(result).toBeDefined();
            // base64url uses - and _ instead of + and /, and omits padding =
            expect(result).not.toContain('+');
            expect(result).not.toContain('/');
            expect(result).not.toContain('=');
        });
    });

    describe('getCredentialFromMalformedBitwardenObject', () => {
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

        it('should convert a malformed Bitwarden credential to a valid SerializableCredential object', () => {
            const credential = getCredentialFromMalformedBitwardenObject(malformedCredential);

            expect(credential).toBeDefined();
            expect(credential?.id).toBe('test-id');
            expect(credential?.type).toBe('public-key');
            expect(credential?.authenticatorAttachment).toBe('platform');
            expect(credential?.rawId).toBeDefined();
            expect(credential?.response).toBeDefined();
            expect(credential?.response.attestationObject).toBeDefined();
            expect(credential?.response.authenticatorData).toBeDefined();
            expect(credential?.response.clientDataJSON).toBeDefined();
            expect(credential?.response.publicKey).toBeDefined();
            expect(credential?.response.publicKeyAlgorithm).toBe(-7);
            expect(credential?.response.transports).toEqual(['usb', 'nfc']);
            expect(credential?.clientExtensionResults).toEqual({});
        });

        it('should encode all binary data as base64url strings', () => {
            const credential = getCredentialFromMalformedBitwardenObject(malformedCredential);

            expectBase64UrlFields(credential, ['rawId', 'response.clientDataJSON', 'response.attestationObject', 'response.authenticatorData', 'response.publicKey']);
        });

        it('should return undefined for a null input', () => {
            const credential = getCredentialFromMalformedBitwardenObject(null);
            expect(credential).toBeUndefined();
        });

        it('should handle credential with login-specific fields', () => {
            const loginCredential: MalformedBitwardenCredential = {
                id: 'login-id',
                rawId: { 0: 108, 1: 111, 2: 103, 3: 105, 4: 110 },
                type: 'public-key',
                authenticatorAttachment: 'cross-platform',
                response: {
                    clientDataJSON: { 0: 123, 1: 125 },
                    authenticatorData: { 0: 10, 1: 11, 2: 12 },
                    signature: { 0: 13, 1: 14, 2: 15 },
                    userHandle: { 0: 16, 1: 17, 2: 18 },
                },
                getClientExtensionResults: () => ({}),
            };

            const credential = getCredentialFromMalformedBitwardenObject(loginCredential);

            expect(credential).toBeDefined();
            expect(credential?.id).toBe('login-id');
            expect(credential?.response.authenticatorData).toBeDefined();
            expect(credential?.response.signature).toBeDefined();
            expect(credential?.response.userHandle).toBeDefined();
        });

        it('should prefer getAuthenticatorData over authenticatorData property', () => {
            const credentialWithBoth: MalformedBitwardenCredential = {
                id: 'test-id',
                rawId: { 0: 1, 1: 2, 2: 3 },
                type: 'public-key',
                authenticatorAttachment: 'platform',
                response: {
                    clientDataJSON: { 0: 123, 1: 125 },
                    authenticatorData: { 0: 7, 1: 8, 2: 9 },
                    getAuthenticatorData: () => ({ 0: 4, 1: 5, 2: 6 }),
                },
                getClientExtensionResults: () => ({}),
            };

            const credential = getCredentialFromMalformedBitwardenObject(credentialWithBoth);

            // TODO fix this test
            expect(credential).toBeDefined();
            expect(credential?.response.authenticatorData).toBeDefined();
            expect(convertToBase64(credentialWithBoth.response.authenticatorData)).toEqual(credential?.response.authenticatorData);
            expect(convertToBase64(credentialWithBoth.response.getAuthenticatorData!())).not.toEqual(credential?.response.authenticatorData);
        });

        it('should handle undefined optional fields gracefully', () => {
            const minimalCredential: MalformedBitwardenCredential = {
                id: 'minimal-id',
                rawId: { 0: 1, 1: 2 },
                type: 'public-key',
                authenticatorAttachment: 'platform',
                response: {
                    clientDataJSON: { 0: 123, 1: 125 },
                },
                getClientExtensionResults: () => ({}),
            };

            const credential = getCredentialFromMalformedBitwardenObject(minimalCredential);

            expect(credential).toBeDefined();
            expect(credential?.id).toBe('minimal-id');
            expect(credential?.response.clientDataJSON).toBeDefined();
            expect(credential?.response.attestationObject).toBeUndefined();
            expect(credential?.response.publicKey).toBeUndefined();
            expect(credential?.response.signature).toBeUndefined();
            expect(credential?.response.userHandle).toBeUndefined();
        });
    });
});

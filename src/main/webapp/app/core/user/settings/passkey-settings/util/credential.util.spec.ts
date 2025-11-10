import { getLoginCredentialWithGracefullyHandlingAuthenticatorIssues, getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues } from './credential.util';
import { MalformedBitwardenRegistrationCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-registration-credential';
import { MalformedBitwardenLoginCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-login-credential';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/errors/invalid-credential.error';
import { afterEach, describe, expect, it, jest } from '@jest/globals';

describe('credential util helper method', () => {
    describe('getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues', () => {
        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should return the credential if it can be stringified', () => {
            const validCredential: Credential = { id: '123', type: 'public-key' };
            const result = getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(validCredential);
            expect(result).toEqual(validCredential);
        });

        /**
         * we expect {@link getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues} to log a warning, therefore we mock the `console.warn` method
         */
        it('should handle malformed Bitwarden credentials gracefully', () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {}); // Suppress console warnings in the test
            const malformedCredential: MalformedBitwardenRegistrationCredential = {
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

            const result = getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(malformedCredential);
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

        it('should throw InvalidCredentialError if credential is invalid', () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {});
            expect(() => getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(undefined)).toThrow(InvalidCredentialError);
        });

        /**
         * we expect {@link getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues} to log a warning, therefore we mock the `console.warn` method
         */
        it('should throw InvalidCredentialError if the credential cannot be processed', () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {}); // Suppress console warnings in the test
            const malformedCredential: Credential = { id: 'mock-id', type: 'public-key' };

            // Mock the toJSON method to simulate an error
            (malformedCredential as any).toJSON = () => {
                throw new TypeError('Illegal invocation');
            };

            expect(() => {
                getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(malformedCredential);
            }).toThrow(InvalidCredentialError);
        });
    });

    describe('getLoginCredentialWithGracefullyHandlingAuthenticatorIssues', () => {
        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should return the credential if it can be stringified', () => {
            const validCredential: Credential = { id: '123', type: 'public-key' };
            const result = getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(validCredential);
            expect(result).toEqual(validCredential);
        });

        /**
         * we expect {@link getLoginCredentialWithGracefullyHandlingAuthenticatorIssues} to log a warning, therefore we mock the `console.warn` method
         */
        it('should handle malformed Bitwarden credentials gracefully during login', () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {}); // Suppress console warnings in the test
            const malformedCredential: MalformedBitwardenLoginCredential = {
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

            const result = getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(malformedCredential);
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

        it('should throw InvalidCredentialError if credential is invalid', () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {});
            expect(() => getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(undefined)).toThrow(InvalidCredentialError);
        });

        /**
         * we expect {@link getLoginCredentialWithGracefullyHandlingAuthenticatorIssues} to log a warning, therefore we mock the `console.warn` method
         */
        it('should throw InvalidCredentialError if the credential cannot be processed', () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {}); // Suppress console warnings in the test
            const malformedCredential: Credential = { id: 'mock-id', type: 'public-key' };

            // Mock the toJSON method to simulate an error
            (malformedCredential as any).toJSON = () => {
                throw new TypeError('Illegal invocation');
            };

            expect(() => {
                getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(malformedCredential);
            }).toThrow(InvalidCredentialError);
        });
    });
});

import { addNewPasskey, getLoginCredentialWithGracefullyHandlingAuthenticatorIssues, getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues } from './credential.util';
import { MalformedBitwardenCredential } from 'app/core/user/settings/passkey-settings/entities/malformed-bitwarden-credential';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { User } from 'app/core/user/user.model';
import { afterEach, describe, expect, it, jest } from '@jest/globals';

describe('credential util helper method', () => {
    describe('getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues', () => {
        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should return the credential if it can be stringified', () => {
            const validCredential = { id: '123' } as unknown as Credential;
            const result = getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(validCredential);
            expect(result).toEqual(validCredential);
        });

        /**
         * we expect {@link getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues} to log a warning, therefore we mock the console.warn method
         */
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
            jest.spyOn(console, 'warn').mockImplementation();
            expect(() => getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(null)).toThrow(InvalidCredentialError);
        });

        /**
         * we expect {@link getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues} to log a warning, therefore we mock the console.warn method
         */
        it('should throw InvalidCredentialError if the credential cannot be processed', () => {
            jest.spyOn(console, 'warn').mockImplementation(); // Suppress console warnings in the test
            const malformedCredential = { id: 'mock-id' } as unknown as Credential;

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
            const validCredential = { id: '123' } as unknown as Credential;
            const result = getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(validCredential);
            expect(result).toEqual(validCredential);
        });

        /**
         * we expect {@link getLoginCredentialWithGracefullyHandlingAuthenticatorIssues} to log a warning, therefore we mock the console.warn method
         */
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
            jest.spyOn(console, 'warn').mockImplementation();
            expect(() => getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(null)).toThrow(InvalidCredentialError);
        });

        /**
         * we expect {@link getLoginCredentialWithGracefullyHandlingAuthenticatorIssues} to log a warning, therefore we mock the console.warn method
         */
        it('should throw InvalidCredentialError if the credential cannot be processed', () => {
            jest.spyOn(console, 'warn').mockImplementation(); // Suppress console warnings in the test
            const malformedCredential = { id: 'mock-id' } as unknown as Credential;

            // Mock the toJSON method to simulate an error
            (malformedCredential as any).toJSON = () => {
                throw new TypeError('Illegal invocation');
            };

            expect(() => {
                getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(malformedCredential);
            }).toThrow(InvalidCredentialError);
        });
    });

    describe('addNewPasskey', () => {
        let mockWebauthnApiService: jest.Mocked<WebauthnApiService>;
        let mockAlertService: jest.Mocked<AlertService>;
        let mockUser: User;

        const originalCredentials = navigator.credentials;

        beforeEach(() => {
            // Mock navigator.credentials.create
            Object.defineProperty(navigator, 'credentials', {
                value: {
                    create: jest.fn(),
                },
                writable: true,
            });

            mockWebauthnApiService = {
                getRegistrationOptions: jest.fn(),
                registerPasskey: jest.fn(),
            } as unknown as jest.Mocked<WebauthnApiService>;

            mockAlertService = {
                addErrorAlert: jest.fn(),
            } as unknown as jest.Mocked<AlertService>;

            mockUser = { email: 'test@example.com' } as User;
        });

        afterEach(() => {
            Object.defineProperty(navigator, 'credentials', {
                value: originalCredentials,
                writable: true,
            });
        });

        it('should throw an error if user is undefined', async () => {
            await expect(addNewPasskey(undefined, mockWebauthnApiService, mockAlertService)).rejects.toThrow('User or Username is not defined');
        });

        it('should handle WebAuthn API errors gracefully', async () => {
            mockWebauthnApiService.getRegistrationOptions.mockRejectedValue(new Error('WebAuthn API error'));

            await expect(addNewPasskey(mockUser, mockWebauthnApiService, mockAlertService)).rejects.toThrow('WebAuthn API error');
        });

        it('should handle InvalidCredentialError if credential is null', async () => {
            mockWebauthnApiService.getRegistrationOptions.mockResolvedValue({} as PublicKeyCredentialCreationOptions);
            jest.spyOn(navigator.credentials, 'create').mockResolvedValue(null);

            await expect(addNewPasskey(mockUser, mockWebauthnApiService, mockAlertService)).rejects.toThrow(Error('Invalid credential'));
            expect(mockAlertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.registration');
        });
    });
});

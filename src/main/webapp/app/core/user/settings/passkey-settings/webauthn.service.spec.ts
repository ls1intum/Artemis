import { TestBed } from '@angular/core/testing';
import { WebauthnService } from './webauthn.service';
import { WebauthnApiService } from './webauthn-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { InvalidCredentialError } from './entities/errors/invalid-credential.error';
import { UserAbortedPasskeyCreationError } from './entities/errors/user-aborted-passkey-creation.error';
import { InvalidStateError } from './entities/errors/invalid-state.error';
import { User } from 'app/core/user/user.model';
import * as credentialUtil from './util/credential.util';
import * as credentialOptionUtil from './util/credential-option.util';
import { encodeAsBase64Url } from 'app/shared/util/base64.util';
import { AccountService } from 'app/core/auth/account.service';
import { signal } from '@angular/core';

describe('WebauthnService', () => {
    let service: WebauthnService;
    let webauthnApiService: jest.Mocked<WebauthnApiService>;
    let alertService: jest.Mocked<AlertService>;
    let accountService: jest.Mocked<AccountService>;

    const mockUser: User = {
        id: 1,
        email: 'test@example.com',
        login: 'testuser',
    } as User;

    const originalCredentials = navigator.credentials;

    beforeEach(() => {
        const webauthnApiServiceMock = {
            getAuthenticationOptions: jest.fn(),
            loginWithPasskey: jest.fn(),
            getRegistrationOptions: jest.fn(),
            registerPasskey: jest.fn(),
        };

        const alertServiceMock = {
            addErrorAlert: jest.fn(),
        };

        const accountServiceMock = {
            userIdentity: signal<User | undefined>({
                id: 1,
                email: 'test@example.com',
                login: 'testuser',
                askToSetupPasskey: true,
                internal: true,
            } as User),
        };

        TestBed.configureTestingModule({
            providers: [
                WebauthnService,
                { provide: WebauthnApiService, useValue: webauthnApiServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: AccountService, useValue: accountServiceMock },
            ],
        });

        service = TestBed.inject(WebauthnService);
        webauthnApiService = TestBed.inject(WebauthnApiService) as jest.Mocked<WebauthnApiService>;
        alertService = TestBed.inject(AlertService) as jest.Mocked<AlertService>;
        accountService = TestBed.inject(AccountService) as jest.Mocked<AccountService>;

        // Mock navigator.credentials
        Object.defineProperty(navigator, 'credentials', {
            value: {
                get: jest.fn(),
                create: jest.fn(),
            },
            writable: true,
            configurable: true,
        });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        Object.defineProperty(navigator, 'credentials', {
            value: originalCredentials,
            writable: true,
            configurable: true,
        });
    });

    describe('loginWithPasskey', () => {
        const mockPublicKeyCredential = {
            id: 'credential-id',
            type: 'public-key',
            rawId: new ArrayBuffer(16),
            response: {
                clientDataJSON: new ArrayBuffer(32),
                authenticatorData: new ArrayBuffer(64),
                signature: new ArrayBuffer(128),
            },
        } as unknown as PublicKeyCredential;

        const setupAuthenticationOptionsMock = () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
                userVerification: 'preferred',
            } as any);
        };

        beforeEach(() => {
            setupAuthenticationOptionsMock();
        });

        it('should successfully login with passkey', async () => {
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            jest.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.loginWithPasskey.mockResolvedValue({ success: true } as any);

            await service.loginWithPasskey();

            expect(webauthnApiService.getAuthenticationOptions).toHaveBeenCalled();
            expect(navigator.credentials.get).toHaveBeenCalledWith({
                publicKey: expect.objectContaining({
                    challenge: expect.any(ArrayBuffer),
                    timeout: 60000,
                    rpId: 'example.com',
                    userVerification: 'preferred',
                }),
            });
            expect(credentialUtil.getLoginCredentialWithGracefullyHandlingAuthenticatorIssues).toHaveBeenCalledWith(mockPublicKeyCredential);
            expect(webauthnApiService.loginWithPasskey).toHaveBeenCalledWith(mockPublicKeyCredential);
            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should throw InvalidCredentialError when credential is null', async () => {
            jest.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors in the test
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(null);

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should throw InvalidCredentialError when credential is undefined', async () => {
            jest.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors in the test
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(undefined as any);

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should throw InvalidCredentialError when credential type is not public-key', async () => {
            jest.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors in the test
            const invalidCredential = { ...mockPublicKeyCredential, type: 'invalid-type' } as unknown as PublicKeyCredential;
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(invalidCredential);

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should throw InvalidCredentialError when getLoginCredentialWithGracefullyHandlingAuthenticatorIssues returns null', async () => {
            jest.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors in the test
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            jest.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(null as any);

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should handle InvalidCredentialError from getLoginCredentialWithGracefullyHandlingAuthenticatorIssues', async () => {
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            jest.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockImplementation(() => {
                throw new InvalidCredentialError();
            });
            jest.spyOn(console, 'error').mockImplementation(() => {});

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            expect(console.error).toHaveBeenCalled();
        });

        it('should handle generic error during login', async () => {
            const genericError = new Error('Network error');
            jest.spyOn(navigator.credentials, 'get').mockRejectedValue(genericError);
            jest.spyOn(console, 'error').mockImplementation(() => {});

            await expect(service.loginWithPasskey()).rejects.toThrow(genericError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.login');
            expect(console.error).toHaveBeenCalledWith(genericError);
        });

        it('should handle error from webauthnApiService.loginWithPasskey', async () => {
            const apiError = new Error('API error');
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            jest.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.loginWithPasskey.mockRejectedValue(apiError);
            jest.spyOn(console, 'error').mockImplementation(() => {});

            await expect(service.loginWithPasskey()).rejects.toThrow(apiError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.login');
            expect(console.error).toHaveBeenCalledWith(apiError);
        });

        it('should update userIdentity signal with isLoggedInWithPasskey set to true', async () => {
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            jest.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.loginWithPasskey.mockResolvedValue({ success: true } as any);

            // Verify initial state (should not have isLoggedInWithPasskey set)
            expect(accountService.userIdentity()?.isLoggedInWithPasskey).toBeUndefined();

            await service.loginWithPasskey();

            // Verify userIdentity signal was updated
            const updatedIdentity = accountService.userIdentity();
            expect(updatedIdentity).toBeDefined();
            expect(updatedIdentity!.isLoggedInWithPasskey).toBeTrue();
            expect(updatedIdentity!.internal).toBeTrue();
            expect(updatedIdentity!.id).toBe(1);
            expect(updatedIdentity!.email).toBe('test@example.com');
            expect(updatedIdentity!.login).toBe('testuser');
        });
    });

    describe('getCredential', () => {
        it('should return a PublicKeyCredential when successful', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            const mockCredential = {
                id: 'credential-id',
                type: 'public-key',
            } as PublicKeyCredential;

            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(mockCredential);

            const result = await service.getCredential();

            expect(result).toBe(mockCredential);
            expect(webauthnApiService.getAuthenticationOptions).toHaveBeenCalled();
            expect(navigator.credentials.get).toHaveBeenCalledWith({
                publicKey: expect.objectContaining({
                    challenge: expect.any(ArrayBuffer),
                }),
            });
        });

        it('should return undefined when credentials.get returns null', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
            } as any);
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue(null);

            const result = await service.getCredential();

            expect(result).toBeUndefined();
        });

        it('should handle allowCredentials mapping correctly', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            const credentialId = new Uint8Array([4, 5, 6, 7, 8]);

            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
                allowCredentials: [
                    {
                        type: 'public-key' as const,
                        id: encodeAsBase64Url(credentialId),
                        transports: ['usb', 'nfc'] as AuthenticatorTransport[],
                    },
                ],
            } as any);
            jest.spyOn(navigator.credentials, 'get').mockResolvedValue({} as PublicKeyCredential);

            await service.getCredential();

            expect(navigator.credentials.get).toHaveBeenCalledWith({
                publicKey: expect.objectContaining({
                    allowCredentials: expect.arrayContaining([
                        expect.objectContaining({
                            type: 'public-key',
                            id: expect.any(ArrayBuffer),
                            transports: ['usb', 'nfc'],
                        }),
                    ]),
                }),
            });
        });
    });

    describe('addNewPasskey', () => {
        const mockRegistrationOptions: PublicKeyCredentialCreationOptions = {
            challenge: new Uint8Array([1, 2, 3]),
            rp: { name: 'Example', id: 'example.com' },
            user: { id: new Uint8Array([4, 5, 6]), name: 'test', displayName: 'Test User' },
            pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
            timeout: 60000,
            attestation: 'none',
        };

        const mockPublicKeyCredential = {
            id: 'credential-id',
            type: 'public-key',
            rawId: new ArrayBuffer(16),
            response: {
                clientDataJSON: new ArrayBuffer(32),
                attestationObject: new ArrayBuffer(64),
                getAuthenticatorData: () => new ArrayBuffer(37),
                getPublicKey: () => new ArrayBuffer(65),
                getPublicKeyAlgorithm: () => -7,
                getTransports: () => ['usb'],
            },
        } as unknown as PublicKeyCredential;

        const setupSuccessfulRegistrationMocks = () => {
            jest.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            jest.spyOn(navigator.credentials, 'create').mockResolvedValue(mockPublicKeyCredential);
            jest.spyOn(credentialUtil, 'getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.registerPasskey.mockResolvedValue({ success: true } as any);
        };

        beforeEach(() => {
            webauthnApiService.getRegistrationOptions.mockResolvedValue(mockRegistrationOptions);
        });

        it('should successfully register a new passkey', async () => {
            setupSuccessfulRegistrationMocks();

            await service.addNewPasskey(mockUser);

            expect(webauthnApiService.getRegistrationOptions).toHaveBeenCalled();
            expect(credentialOptionUtil.createCredentialOptions).toHaveBeenCalledWith(mockRegistrationOptions, mockUser);
            expect(navigator.credentials.create).toHaveBeenCalledWith({
                publicKey: mockRegistrationOptions,
            });
            expect(credentialUtil.getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues).toHaveBeenCalledWith(mockPublicKeyCredential);
            expect(webauthnApiService.registerPasskey).toHaveBeenCalledWith({
                publicKey: {
                    credential: mockPublicKeyCredential,
                    label: expect.stringContaining(mockUser.email!),
                },
            });
            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should throw error when user is undefined', async () => {
            await expect(service.addNewPasskey(undefined)).rejects.toThrow('User or Username is not defined');
        });

        it('should not show alert when user cancels passkey creation', async () => {
            const userAbortedError = new Error('User cancelled');
            userAbortedError.name = UserAbortedPasskeyCreationError.name;
            (userAbortedError as any).code = UserAbortedPasskeyCreationError.code;

            jest.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            jest.spyOn(navigator.credentials, 'create').mockRejectedValue(userAbortedError);

            await service.addNewPasskey(mockUser);

            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should handle InvalidCredentialError', async () => {
            jest.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            jest.spyOn(navigator.credentials, 'create').mockResolvedValue(mockPublicKeyCredential);
            jest.spyOn(credentialUtil, 'getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues').mockImplementation(() => {
                throw new InvalidCredentialError();
            });

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should handle InvalidStateError when passkey already registered', async () => {
            const invalidStateError = new Error('Passkey already registered');
            invalidStateError.name = InvalidStateError.name;
            (invalidStateError as any).code = InvalidStateError.authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode;

            jest.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            jest.spyOn(navigator.credentials, 'create').mockRejectedValue(invalidStateError);

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(invalidStateError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.passkeyAlreadyRegistered');
        });

        it('should handle generic registration error', async () => {
            const genericError = new Error('Registration failed');
            jest.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            jest.spyOn(navigator.credentials, 'create').mockRejectedValue(genericError);

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(genericError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.registration');
        });

        it('should handle error from webauthnApiService.registerPasskey', async () => {
            const apiError = new Error('API registration error');
            jest.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            jest.spyOn(navigator.credentials, 'create').mockResolvedValue(mockPublicKeyCredential);
            jest.spyOn(credentialUtil, 'getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.registerPasskey.mockRejectedValue(apiError);

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(apiError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.registration');
        });

        it('should handle null credential from navigator.credentials.create', async () => {
            jest.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            jest.spyOn(navigator.credentials, 'create').mockResolvedValue(null);
            jest.spyOn(credentialUtil, 'getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues').mockImplementation(() => {
                throw new InvalidCredentialError();
            });

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should include OS in passkey label', async () => {
            setupSuccessfulRegistrationMocks();

            await service.addNewPasskey(mockUser);

            expect(webauthnApiService.registerPasskey).toHaveBeenCalledWith({
                publicKey: {
                    credential: expect.any(Object),
                    label: expect.stringMatching(/test@example.com - (macOS|Windows|Linux|iOS|Android|Unknown)/),
                },
            });
        });

        it('should update userIdentity signal with askToSetupPasskey set to false', async () => {
            setupSuccessfulRegistrationMocks();

            // Verify initial state
            expect(accountService.userIdentity()?.askToSetupPasskey).toBeTrue();

            await service.addNewPasskey(mockUser);

            // Verify userIdentity signal was updated
            const updatedIdentity = accountService.userIdentity();
            expect(updatedIdentity).toBeDefined();
            expect(updatedIdentity!.askToSetupPasskey).toBeFalse();
            expect(updatedIdentity!.internal).toBeTrue();
            expect(updatedIdentity!.id).toBe(1);
            expect(updatedIdentity!.email).toBe('test@example.com');
            expect(updatedIdentity!.login).toBe('testuser');
        });
    });
});

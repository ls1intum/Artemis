import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { WebauthnService } from './webauthn.service';
import { WebauthnApiService } from './webauthn-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { InvalidCredentialError } from './entities/errors/invalid-credential.error';
import { UserAbortedPasskeyCreationError } from './entities/errors/user-aborted-passkey-creation.error';
import { InvalidStateError } from './entities/errors/invalid-state.error';
import { PasskeyAbortError } from './entities/errors/passkey-abort.error';
import { User } from 'app/core/user/user.model';
import * as credentialUtil from './util/credential.util';
import * as credentialOptionUtil from './util/credential-option.util';
import { encodeAsBase64Url } from 'app/shared/util/base64.util';
import { AccountService } from 'app/core/auth/account.service';
import { signal } from '@angular/core';

type MockedWebauthnApiService = {
    getAuthenticationOptions: ReturnType<typeof vi.fn>;
    loginWithPasskey: ReturnType<typeof vi.fn>;
    getRegistrationOptions: ReturnType<typeof vi.fn>;
    registerPasskey: ReturnType<typeof vi.fn>;
};

type MockedAlertService = {
    addErrorAlert: ReturnType<typeof vi.fn>;
};

type MockedAccountService = {
    userIdentity: ReturnType<typeof signal<User | undefined>>;
    identity: ReturnType<typeof vi.fn>;
};

describe('WebauthnService', () => {
    setupTestBed({ zoneless: true });

    let service: WebauthnService;
    let webauthnApiService: MockedWebauthnApiService;
    let alertService: MockedAlertService;
    let accountService: MockedAccountService;

    const mockUser: User = {
        id: 1,
        email: 'test@example.com',
        login: 'testuser',
    } as User;

    const originalCredentials = navigator.credentials;

    beforeEach(() => {
        const webauthnApiServiceMock = {
            getAuthenticationOptions: vi.fn(),
            loginWithPasskey: vi.fn(),
            getRegistrationOptions: vi.fn(),
            registerPasskey: vi.fn(),
        };

        const alertServiceMock = {
            addErrorAlert: vi.fn(),
        };

        const accountServiceMock = {
            userIdentity: signal<User | undefined>({
                id: 1,
                email: 'test@example.com',
                login: 'testuser',
                askToSetupPasskey: true,
                internal: true,
            } as User),
            identity: vi.fn(),
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
        webauthnApiService = TestBed.inject(WebauthnApiService) as unknown as MockedWebauthnApiService;
        alertService = TestBed.inject(AlertService) as unknown as MockedAlertService;
        accountService = TestBed.inject(AccountService) as unknown as MockedAccountService;

        // Mock navigator.credentials
        Object.defineProperty(navigator, 'credentials', {
            value: {
                get: vi.fn(),
                create: vi.fn(),
            },
            writable: true,
            configurable: true,
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
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
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.loginWithPasskey.mockResolvedValue({ success: true } as any);
            accountService.identity.mockResolvedValue({} as any);

            await service.loginWithPasskey();

            expect(webauthnApiService.getAuthenticationOptions).toHaveBeenCalled();
            expect(navigator.credentials.get).toHaveBeenCalledWith(
                expect.objectContaining({
                    publicKey: expect.objectContaining({
                        challenge: expect.any(ArrayBuffer),
                        timeout: 60000,
                        rpId: 'example.com',
                        userVerification: 'preferred',
                    }),
                    signal: expect.any(AbortSignal),
                }),
            );
            expect(credentialUtil.getLoginCredentialWithGracefullyHandlingAuthenticatorIssues).toHaveBeenCalledWith(mockPublicKeyCredential);
            expect(webauthnApiService.loginWithPasskey).toHaveBeenCalledWith(mockPublicKeyCredential);
            expect(accountService.identity).toHaveBeenCalledWith(true);
            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should throw InvalidCredentialError when credential is null', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors in the test
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(null);

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should throw InvalidCredentialError when credential is undefined', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors in the test
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(undefined as any);

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should throw InvalidCredentialError when credential type is not public-key', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors in the test
            const invalidCredential = { ...mockPublicKeyCredential, type: 'invalid-type' } as unknown as PublicKeyCredential;
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(invalidCredential);

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should throw InvalidCredentialError when getLoginCredentialWithGracefullyHandlingAuthenticatorIssues returns null', async () => {
            vi.spyOn(console, 'error').mockImplementation(() => {}); // Suppress console errors in the test
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(null as any);

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should handle InvalidCredentialError from getLoginCredentialWithGracefullyHandlingAuthenticatorIssues', async () => {
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockImplementation(() => {
                throw new InvalidCredentialError();
            });
            vi.spyOn(console, 'error').mockImplementation(() => {});

            await expect(service.loginWithPasskey()).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            expect(console.error).toHaveBeenCalled();
        });

        it('should handle generic error during login', async () => {
            const genericError = new Error('Network error');
            vi.spyOn(navigator.credentials, 'get').mockRejectedValue(genericError);
            vi.spyOn(console, 'error').mockImplementation(() => {});

            await expect(service.loginWithPasskey()).rejects.toThrow(genericError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.login');
            expect(console.error).toHaveBeenCalledWith(genericError);
        });

        it('should handle error from webauthnApiService.loginWithPasskey', async () => {
            const apiError = new Error('API error');
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.loginWithPasskey.mockRejectedValue(apiError);
            vi.spyOn(console, 'error').mockImplementation(() => {});

            await expect(service.loginWithPasskey()).rejects.toThrow(apiError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.login');
            expect(console.error).toHaveBeenCalledWith(apiError);
        });

        it('should call accountService.identity after successful login', async () => {
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockPublicKeyCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.loginWithPasskey.mockResolvedValue({ success: true } as any);
            accountService.identity.mockResolvedValue({} as any);

            await service.loginWithPasskey();

            expect(webauthnApiService.loginWithPasskey).toHaveBeenCalledWith(mockPublicKeyCredential);
            expect(accountService.identity).toHaveBeenCalledWith(true);
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
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockCredential);

            const result = await service.getCredential();

            expect(result).toBe(mockCredential);
            expect(webauthnApiService.getAuthenticationOptions).toHaveBeenCalled();
            expect(navigator.credentials.get).toHaveBeenCalledWith(
                expect.objectContaining({
                    publicKey: expect.objectContaining({
                        challenge: expect.any(ArrayBuffer),
                    }),
                    signal: expect.any(AbortSignal),
                }),
            );
        });

        it('should return undefined when credentials.get returns null', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
            } as any);
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(null);

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
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue({} as PublicKeyCredential);

            await service.getCredential();

            expect(navigator.credentials.get).toHaveBeenCalledWith(
                expect.objectContaining({
                    publicKey: expect.objectContaining({
                        allowCredentials: expect.arrayContaining([
                            expect.objectContaining({
                                type: 'public-key',
                                id: expect.any(ArrayBuffer),
                                transports: ['usb', 'nfc'],
                            }),
                        ]),
                    }),
                }),
            );
        });
    });

    describe('getCredential with conditional mediation', () => {
        const setupAuthMock = () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
                userVerification: 'preferred',
            } as any);
        };

        beforeEach(() => {
            setupAuthMock();
        });

        it('should pass mediation conditional and signal when isConditional is true', async () => {
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue({ type: 'public-key' } as PublicKeyCredential);

            await service.getCredential(true);

            expect(navigator.credentials.get).toHaveBeenCalledWith(
                expect.objectContaining({
                    publicKey: expect.any(Object),
                    mediation: 'conditional',
                    signal: expect.any(AbortSignal),
                }),
            );
        });

        it('should not pass mediation when isConditional is false', async () => {
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue({ type: 'public-key' } as PublicKeyCredential);

            await service.getCredential(false);

            const callArgs = (navigator.credentials.get as ReturnType<typeof vi.fn>).mock.calls[0][0];
            expect(callArgs.mediation).toBeUndefined();
            expect(callArgs.signal).toEqual(expect.any(AbortSignal));
        });
    });

    describe('abortPendingCredentialRequest', () => {
        it('should abort the current AbortController with PasskeyAbortError', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);

            let capturedSignal: AbortSignal | undefined;
            vi.spyOn(navigator.credentials, 'get').mockImplementation((options: any) => {
                capturedSignal = options?.signal;
                return new Promise(() => {}); // never resolves
            });

            // Start a conditional request — must await the authentication options fetch
            void service.getCredential(true);
            // Wait for the async getAuthenticationOptions mock to resolve
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(capturedSignal).toBeDefined();
            expect(capturedSignal!.aborted).toBeFalse();

            service.abortPendingCredentialRequest();

            expect(capturedSignal!.aborted).toBeTrue();
            expect(capturedSignal!.reason).toBeInstanceOf(PasskeyAbortError);
        });

        it('should abort the signal even when called during pending getAuthenticationOptions', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            let resolveOptions: (value: any) => void;
            const optionsPromise = new Promise((resolve) => {
                resolveOptions = resolve;
            });
            webauthnApiService.getAuthenticationOptions.mockReturnValue(optionsPromise as any);

            let capturedSignal: AbortSignal | undefined;
            vi.spyOn(navigator.credentials, 'get').mockImplementation((options: any) => {
                capturedSignal = options?.signal;
                return new Promise(() => {}); // never resolves
            });

            // Start a conditional request — getAuthenticationOptions is still pending
            void service.getCredential(true);

            // Abort BEFORE the HTTP response arrives (simulates ngOnDestroy during fetch)
            service.abortPendingCredentialRequest();

            // Now resolve the HTTP response
            resolveOptions!({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            });
            await new Promise((resolve) => setTimeout(resolve, 0));

            // The signal passed to navigator.credentials.get should be the original (aborted) one
            expect(capturedSignal).toBeDefined();
            expect(capturedSignal!.aborted).toBeTrue();
            expect(capturedSignal!.reason).toBeInstanceOf(PasskeyAbortError);
        });
    });

    describe('loginWithPasskey with conditional mediation', () => {
        it('should abort pending requests when called without conditional', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            const mockCredential = { type: 'public-key' } as PublicKeyCredential;
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockCredential as any);
            webauthnApiService.loginWithPasskey.mockResolvedValue({ success: true } as any);
            accountService.identity.mockResolvedValue({} as any);
            const abortSpy = vi.spyOn(service, 'abortPendingCredentialRequest');

            await service.loginWithPasskey(false);

            expect(abortSpy).toHaveBeenCalled();
        });

        it('should not abort pending requests when called with conditional', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            const mockCredential = { type: 'public-key' } as PublicKeyCredential;
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockCredential as any);
            webauthnApiService.loginWithPasskey.mockResolvedValue({ success: true } as any);
            accountService.identity.mockResolvedValue({} as any);
            const abortSpy = vi.spyOn(service, 'abortPendingCredentialRequest');

            await service.loginWithPasskey(true);

            expect(abortSpy).not.toHaveBeenCalled();
        });

        it('should rethrow PasskeyAbortError without showing alerts when conditional', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            vi.spyOn(navigator.credentials, 'get').mockRejectedValue(new PasskeyAbortError());

            await expect(service.loginWithPasskey(true)).rejects.toThrow(PasskeyAbortError);
            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should rethrow DOMException AbortError without showing alerts when conditional', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            vi.spyOn(navigator.credentials, 'get').mockRejectedValue(new DOMException('Aborted', 'AbortError'));

            await expect(service.loginWithPasskey(true)).rejects.toThrow(DOMException);
            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should rethrow DOMException NotAllowedError without showing alerts when conditional', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            vi.spyOn(navigator.credentials, 'get').mockRejectedValue(new DOMException('User cancelled', 'NotAllowedError'));

            await expect(service.loginWithPasskey(true)).rejects.toThrow(DOMException);
            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should show error alert for 401 errors even when conditional', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            const mockCredential = { type: 'public-key' } as PublicKeyCredential;
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockCredential as any);
            const error401 = { status: 401 } as any;
            webauthnApiService.loginWithPasskey.mockRejectedValue(error401);
            vi.spyOn(console, 'error').mockImplementation(() => {});

            await expect(service.loginWithPasskey(true)).rejects.toEqual(error401);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.loginDeactivated');
        });

        it('should show error alert for generic errors even when conditional', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            const genericError = new Error('Server error');
            vi.spyOn(navigator.credentials, 'get').mockRejectedValue(genericError);
            vi.spyOn(console, 'error').mockImplementation(() => {});

            await expect(service.loginWithPasskey(true)).rejects.toThrow(genericError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.login');
        });
    });

    describe('getCredential request serialization', () => {
        it('should wait for pending options request before starting a new one', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            const callOrder: string[] = [];

            let resolveFirst: (value: any) => void;
            const firstOptionsPromise = new Promise((resolve) => {
                resolveFirst = resolve;
            });

            webauthnApiService.getAuthenticationOptions
                .mockImplementationOnce(() => {
                    callOrder.push('first-start');
                    return firstOptionsPromise.then((val) => {
                        callOrder.push('first-end');
                        return val;
                    });
                })
                .mockImplementationOnce(() => {
                    callOrder.push('second-start');
                    return Promise.resolve({
                        challenge: encodeAsBase64Url(challenge),
                        timeout: 60000,
                        rpId: 'example.com',
                    });
                });

            vi.spyOn(navigator.credentials, 'get').mockResolvedValue({ type: 'public-key' } as PublicKeyCredential);

            // Start first request
            const first = service.getCredential(true);
            // Start second request before first completes
            const second = service.getCredential(false);

            // Resolve the first request
            resolveFirst!({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            });

            await first;
            await second;

            // The second request should have waited for the first to complete
            expect(callOrder[0]).toBe('first-start');
            expect(callOrder[1]).toBe('first-end');
            expect(callOrder[2]).toBe('second-start');
        });
    });

    describe('startConditionalMediation', () => {
        it('should call loginWithPasskey with conditional=true and invoke onSuccess callback', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            const mockCredential = { type: 'public-key' } as PublicKeyCredential;
            vi.spyOn(navigator.credentials, 'get').mockResolvedValue(mockCredential);
            vi.spyOn(credentialUtil, 'getLoginCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockCredential as any);
            webauthnApiService.loginWithPasskey.mockResolvedValue({ success: true } as any);
            accountService.identity.mockResolvedValue({} as any);

            const onSuccess = vi.fn();
            service.startConditionalMediation(onSuccess);

            // Wait for the async flow to complete
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(onSuccess).toHaveBeenCalledOnce();
        });

        it('should silently handle PasskeyAbortError without calling onSuccess', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            vi.spyOn(navigator.credentials, 'get').mockRejectedValue(new PasskeyAbortError());

            const onSuccess = vi.fn();
            service.startConditionalMediation(onSuccess);

            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(onSuccess).not.toHaveBeenCalled();
        });

        it('should silently handle DOMException AbortError without calling onSuccess', async () => {
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            vi.spyOn(navigator.credentials, 'get').mockRejectedValue(new DOMException('Aborted', 'AbortError'));

            const onSuccess = vi.fn();
            service.startConditionalMediation(onSuccess);

            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(onSuccess).not.toHaveBeenCalled();
        });

        it('should retry once on NotAllowedError (user cancelled)', async () => {
            vi.spyOn(console, 'warn').mockImplementation(() => {});
            const challenge = new Uint8Array([1, 2, 3]);
            webauthnApiService.getAuthenticationOptions.mockResolvedValue({
                challenge: encodeAsBase64Url(challenge),
                timeout: 60000,
                rpId: 'example.com',
            } as any);
            const notAllowedError = new DOMException('User cancelled', 'NotAllowedError');
            vi.spyOn(navigator.credentials, 'get').mockRejectedValue(notAllowedError);

            const onSuccess = vi.fn();
            service.startConditionalMediation(onSuccess);

            // Wait for the retry to complete
            await new Promise((resolve) => setTimeout(resolve, 10));

            // loginWithPasskey should have been called twice (initial + one retry)
            expect(webauthnApiService.getAuthenticationOptions).toHaveBeenCalledTimes(2);
            expect(onSuccess).not.toHaveBeenCalled();
        });
    });

    describe('stopConditionalMediation', () => {
        it('should abort pending credential request', () => {
            const abortSpy = vi.spyOn(service, 'abortPendingCredentialRequest');

            service.stopConditionalMediation();

            expect(abortSpy).toHaveBeenCalledOnce();
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
            vi.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            vi.spyOn(navigator.credentials, 'create').mockResolvedValue(mockPublicKeyCredential);
            vi.spyOn(credentialUtil, 'getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
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

            vi.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            vi.spyOn(navigator.credentials, 'create').mockRejectedValue(userAbortedError);

            await service.addNewPasskey(mockUser);

            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should handle InvalidCredentialError', async () => {
            vi.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            vi.spyOn(navigator.credentials, 'create').mockResolvedValue(mockPublicKeyCredential);
            vi.spyOn(credentialUtil, 'getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues').mockImplementation(() => {
                throw new InvalidCredentialError();
            });

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(InvalidCredentialError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should handle InvalidStateError when passkey already registered', async () => {
            const invalidStateError = new Error('Passkey already registered');
            invalidStateError.name = InvalidStateError.name;
            (invalidStateError as any).code = InvalidStateError.authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode;

            vi.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            vi.spyOn(navigator.credentials, 'create').mockRejectedValue(invalidStateError);

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(invalidStateError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.passkeyAlreadyRegistered');
        });

        it('should handle generic registration error', async () => {
            const genericError = new Error('Registration failed');
            vi.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            vi.spyOn(navigator.credentials, 'create').mockRejectedValue(genericError);

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(genericError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.registration');
        });

        it('should handle error from webauthnApiService.registerPasskey', async () => {
            const apiError = new Error('API registration error');
            vi.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            vi.spyOn(navigator.credentials, 'create').mockResolvedValue(mockPublicKeyCredential);
            vi.spyOn(credentialUtil, 'getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues').mockReturnValue(mockPublicKeyCredential as any);
            webauthnApiService.registerPasskey.mockRejectedValue(apiError);

            await expect(service.addNewPasskey(mockUser)).rejects.toThrow(apiError);
            expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.registration');
        });

        it('should handle null credential from navigator.credentials.create', async () => {
            vi.spyOn(credentialOptionUtil, 'createCredentialOptions').mockReturnValue(mockRegistrationOptions);
            vi.spyOn(navigator.credentials, 'create').mockResolvedValue(null);
            vi.spyOn(credentialUtil, 'getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues').mockImplementation(() => {
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
            expect(accountService.userIdentity()?.askToSetupPasskey).toBe(true);

            await service.addNewPasskey(mockUser);

            // Verify userIdentity signal was updated
            const updatedIdentity = accountService.userIdentity();
            expect(updatedIdentity).toBeDefined();
            expect(updatedIdentity!.askToSetupPasskey).toBe(false);
            expect(updatedIdentity!.internal).toBe(true);
            expect(updatedIdentity!.id).toBe(1);
            expect(updatedIdentity!.email).toBe('test@example.com');
            expect(updatedIdentity!.login).toBe('testuser');
        });
    });
});

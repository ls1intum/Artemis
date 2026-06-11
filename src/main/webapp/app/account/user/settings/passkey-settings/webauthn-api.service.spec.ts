import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { WebauthnApiService } from './webauthn-api.service';
import { RegisterPasskeyDTO } from './dto/register-passkey.dto';
import { RegisterPasskeyResponseDTO } from './dto/register-passkey-response.dto';
import { PasskeyLoginResponseDTO } from './dto/passkey-login-response.dto';

// Mock data
const mockRegistrationOptions: PublicKeyCredentialCreationOptions = {
    challenge: new Uint8Array([1, 2, 3, 4]),
    rp: { name: 'Test RP', id: 'test.example.com' },
    user: { id: new Uint8Array([5, 6, 7, 8]), name: 'testuser', displayName: 'Test User' },
    pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
    timeout: 60000,
    attestation: 'none',
};

const mockAuthenticationOptions: PublicKeyCredentialRequestOptions = {
    challenge: new Uint8Array([9, 10, 11, 12]),
    timeout: 60000,
    rpId: 'test.example.com',
    userVerification: 'preferred',
};

const mockRegisterPasskeyDTO: RegisterPasskeyDTO = {
    publicKey: {
        credential: null,
        label: 'Test Passkey',
    },
};

const mockRegisterPasskeyResponse: RegisterPasskeyResponseDTO = {
    success: true,
};

const mockPasskeyLoginResponse: PasskeyLoginResponseDTO = {
    redirectUrl: '/home',
    authenticated: true,
};

describe('WebauthnApiService', () => {
    setupTestBed({ zoneless: true });

    let service: WebauthnApiService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [WebauthnApiService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(WebauthnApiService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getRegistrationOptions', () => {
        it('should get registration options', async () => {
            const promise = service.getRegistrationOptions();
            const req = httpMock.expectOne('/webauthn/register/options');
            expect(req.request.method).toBe('POST');
            req.flush(mockRegistrationOptions);
            const result = await promise;
            expect(result).toEqual(mockRegistrationOptions);
        });

        it('should handle registration options with all pubKeyCredParams types', async () => {
            const optionsWithMultipleAlgs: PublicKeyCredentialCreationOptions = {
                ...mockRegistrationOptions,
                pubKeyCredParams: [
                    { type: 'public-key', alg: -7 },
                    { type: 'public-key', alg: -257 },
                ],
            };
            const promise = service.getRegistrationOptions();
            const req = httpMock.expectOne('/webauthn/register/options');
            expect(req.request.method).toBe('POST');
            req.flush(optionsWithMultipleAlgs);
            const result = await promise;
            expect(result.pubKeyCredParams).toHaveLength(2);
        });
    });

    describe('registerPasskey', () => {
        it('should register a new passkey', async () => {
            const promise = service.registerPasskey(mockRegisterPasskeyDTO);
            const req = httpMock.expectOne('/webauthn/register');
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(mockRegisterPasskeyDTO);
            req.flush(mockRegisterPasskeyResponse);
            const result = await promise;
            expect(result).toEqual(mockRegisterPasskeyResponse);
        });

        it('should handle registration with credential', async () => {
            const dtoWithCredential: RegisterPasskeyDTO = {
                publicKey: {
                    credential: {
                        id: 'test-credential-id',
                        type: 'public-key',
                    } as Credential,
                    label: 'My Passkey',
                },
            };
            const promise = service.registerPasskey(dtoWithCredential);
            const req = httpMock.expectOne('/webauthn/register');
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(dtoWithCredential);
            req.flush(mockRegisterPasskeyResponse);
            const result = await promise;
            expect(result.success).toBe(true);
        });

        it('should handle registration failure response', async () => {
            const failureResponse: RegisterPasskeyResponseDTO = {
                success: false,
            };
            const promise = service.registerPasskey(mockRegisterPasskeyDTO);
            const req = httpMock.expectOne('/webauthn/register');
            req.flush(failureResponse);
            const result = await promise;
            expect(result.success).toBe(false);
        });
    });

    describe('getAuthenticationOptions', () => {
        it('should get authentication options', async () => {
            const promise = service.getAuthenticationOptions();
            const req = httpMock.expectOne('/webauthn/authenticate/options');
            expect(req.request.method).toBe('POST');
            req.flush(mockAuthenticationOptions);
            const result = await promise;
            expect(result).toEqual(mockAuthenticationOptions);
        });

        it('should handle authentication options with allowCredentials', async () => {
            const optionsWithAllowCredentials: PublicKeyCredentialRequestOptions = {
                ...mockAuthenticationOptions,
                allowCredentials: [
                    {
                        type: 'public-key',
                        id: new Uint8Array([1, 2, 3]),
                        transports: ['usb', 'nfc'],
                    },
                ],
            };
            const promise = service.getAuthenticationOptions();
            const req = httpMock.expectOne('/webauthn/authenticate/options');
            req.flush(optionsWithAllowCredentials);
            const result = await promise;
            expect(result.allowCredentials).toBeDefined();
            expect(result.allowCredentials).toHaveLength(1);
        });
    });

    describe('loginWithPasskey', () => {
        it('should login with passkey', async () => {
            const mockCredential = {
                id: 'test-credential-id',
                type: 'public-key',
                rawId: new ArrayBuffer(16),
                response: {
                    clientDataJSON: new ArrayBuffer(32),
                    authenticatorData: new ArrayBuffer(64),
                    signature: new ArrayBuffer(128),
                },
            } as unknown as PublicKeyCredential;

            const promise = service.loginWithPasskey(mockCredential);
            const req = httpMock.expectOne('/login/webauthn');
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(mockCredential);
            req.flush(mockPasskeyLoginResponse);
            const result = await promise;
            expect(result).toEqual(mockPasskeyLoginResponse);
        });

        it('should handle login response with redirect', async () => {
            const mockCredential = {
                id: 'test-credential-id',
                type: 'public-key',
            } as unknown as PublicKeyCredential;

            const responseWithRedirect: PasskeyLoginResponseDTO = {
                redirectUrl: '/dashboard',
                authenticated: true,
            };

            const promise = service.loginWithPasskey(mockCredential);
            const req = httpMock.expectOne('/login/webauthn');
            req.flush(responseWithRedirect);
            const result = await promise;
            expect(result.redirectUrl).toBe('/dashboard');
            expect(result.authenticated).toBe(true);
        });

        it('should handle failed login response', async () => {
            const mockCredential = {
                id: 'test-credential-id',
                type: 'public-key',
            } as unknown as PublicKeyCredential;

            const failedResponse: PasskeyLoginResponseDTO = {
                redirectUrl: '/login',
                authenticated: false,
            };

            const promise = service.loginWithPasskey(mockCredential);
            const req = httpMock.expectOne('/login/webauthn');
            req.flush(failedResponse);
            const result = await promise;
            expect(result.authenticated).toBe(false);
        });
    });
});

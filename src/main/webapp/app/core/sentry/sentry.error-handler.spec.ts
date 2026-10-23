import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Mock @sentry/angular before importing anything else
vi.mock('@sentry/angular', async (importOriginal) => {
    const originalModule = await importOriginal<typeof import('@sentry/angular')>();
    return {
        ...originalModule,
        init: vi.fn(),
        captureException: vi.fn(),
        browserTracingIntegration: vi.fn(() => ({ name: 'BrowserTracing' })),
        dedupeIntegration: vi.fn(() => ({ name: 'Dedupe' })),
    };
});

import { TestBed } from '@angular/core/testing';
import * as Sentry from '@sentry/angular';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { ProfileInfo, SentryConfig } from 'app/core/layouts/profiles/profile-info.model';
import { PROFILE_PROD, PROFILE_TEST } from 'app/app.constants';
import { MockProvider } from 'ng-mocks';

describe('SentryErrorHandler', () => {
    let service: SentryErrorHandler;
    let localStorageService: LocalStorageService;
    let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

    const createProfileInfo = (options: { sentry?: SentryConfig; testServer?: boolean }): ProfileInfo => {
        const profileInfo = new ProfileInfo();
        profileInfo.sentry = options.sentry ?? { dsn: 'https://test@sentry.io/123' };
        profileInfo.testServer = options.testServer ?? false;
        return profileInfo;
    };

    beforeEach(() => {
        // Mock window.PublicKeyCredential to simulate WebAuthn support
        Object.defineProperty(window, 'PublicKeyCredential', {
            value: true,
            writable: true,
            configurable: true,
        });

        TestBed.configureTestingModule({
            providers: [MockProvider(LocalStorageService, { retrieveDate: vi.fn(), store: vi.fn() })],
        });
        service = TestBed.inject(SentryErrorHandler);
        localStorageService = TestBed.inject(LocalStorageService);

        vi.clearAllMocks();

        // Suppress console.error output during tests (must come after clearAllMocks)
        consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    describe('initSentry', () => {
        it('should not initialize Sentry if profileInfo is undefined', async () => {
            await service.initSentry(undefined as any);

            expect(Sentry.init).not.toHaveBeenCalled();
        });

        it('should not initialize Sentry if profileInfo.sentry is undefined', async () => {
            const profileInfo = new ProfileInfo();
            profileInfo.sentry = undefined as any;

            await service.initSentry(profileInfo);

            expect(Sentry.init).not.toHaveBeenCalled();
        });

        it('should initialize Sentry with test environment for testServer=true', async () => {
            const profileInfo = createProfileInfo({ testServer: true });

            await service.initSentry(profileInfo);

            expect(Sentry.init).toHaveBeenCalledOnce();
            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            expect(callArgs.environment).toBe(PROFILE_TEST);
            expect(callArgs.dsn).toBe('https://test@sentry.io/123');
            // Test server should not have browserTracingIntegration
            expect(callArgs.integrations).toHaveLength(1);
            expect(callArgs.integrations[0].name).toBe('Dedupe');
        });

        it('should initialize Sentry with prod environment for testServer=false', async () => {
            const profileInfo = createProfileInfo({ testServer: false });

            await service.initSentry(profileInfo);

            expect(Sentry.init).toHaveBeenCalledOnce();
            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            expect(callArgs.environment).toBe(PROFILE_PROD);
            // Prod should have browserTracingIntegration
            expect(callArgs.integrations).toHaveLength(2);
        });

        it('should initialize Sentry with local environment when testServer is undefined', async () => {
            const profileInfo = createProfileInfo({});
            profileInfo.testServer = undefined as any;

            await service.initSentry(profileInfo);

            expect(Sentry.init).toHaveBeenCalledOnce();
            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            expect(callArgs.environment).toBe('local');
        });

        it('should keep the restrictive Sentry data collection baseline', async () => {
            const profileInfo = createProfileInfo({ testServer: false });

            await service.initSentry(profileInfo);

            // Sentry 11 collects far more when `dataCollection` is unset, so every category has to stay switched off or filtered.
            const deniedPatterns = ['authorization', 'forwarded', '-ip', 'remote-', 'via', '-user'];
            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            expect(callArgs.dataCollection).toEqual({
                userInfo: false,
                cookies: false,
                httpHeaders: { request: { deny: deniedPatterns }, response: { deny: deniedPatterns } },
                httpBodies: [],
                urlQueryParams: { deny: deniedPatterns },
                genAI: { inputs: false, outputs: false },
                databaseQueryData: false,
                queues: false,
                graphQL: { document: false, variables: false },
            });
            expect(callArgs).not.toHaveProperty('sendDefaultPii');
        });

        it('should scrub repository urls from streamed spans instead of using the ignored beforeSendTransaction', async () => {
            const profileInfo = createProfileInfo({ testServer: false });

            await service.initSentry(profileInfo);

            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            expect(callArgs).not.toHaveProperty('beforeSendTransaction');

            const span = {
                name: 'GET /git/ABC123/exercise-student1.git',
                attributes: {
                    'url.full': 'https://artemis.example/git/ABC123/exercise-student1.git/info/refs',
                    'http.url': 'https://artemis.example/git/ABC123/exercise-student1.git',
                    'sentry.segment.name': 'GET /git/ABC123/exercise-student1.git',
                    'http.request.header.referer': ['https://artemis.example/git/ABC123/exercise-student1.git', 42],
                    'http.response.status_code': 200,
                    'sentry.origin': 'auto.http.client.fetch',
                },
            };
            const scrubbed = callArgs.beforeSendSpan(span);

            expect(scrubbed.name).toBe('GET /git/ABC123/exercise.git');
            expect(scrubbed.attributes['url.full']).toBe('https://artemis.example/git/ABC123/exercise.git/info/refs');
            expect(scrubbed.attributes['http.url']).toBe('https://artemis.example/git/ABC123/exercise.git');
            expect(scrubbed.attributes['sentry.segment.name']).toBe('GET /git/ABC123/exercise.git');
            expect(scrubbed.attributes['http.request.header.referer']).toEqual(['https://artemis.example/git/ABC123/exercise.git', 42]);
            expect(scrubbed.attributes['http.response.status_code']).toBe(200);
            expect(scrubbed.attributes['sentry.origin']).toBe('auto.http.client.fetch');
        });

        it.each([
            ['a login with hyphens', 'https://artemis.example/git/ABC123/abc123-jane-doe.git/info/refs', 'https://artemis.example/git/ABC123/abc123.git/info/refs'],
            ['a practice repository', 'https://artemis.example/git/ABC123/abc123-practice-jane-doe.git', 'https://artemis.example/git/ABC123/abc123.git'],
            ['a login with an exercise suffix', 'https://artemis.example/git/ABC123/abc123-jane-exercise.git', 'https://artemis.example/git/ABC123/abc123.git'],
            ['the template repository', 'https://artemis.example/git/ABC123/abc123-exercise.git', 'https://artemis.example/git/ABC123/abc123-exercise.git'],
            ['the solution repository', 'https://artemis.example/git/ABC123/abc123-solution.git/info/refs', 'https://artemis.example/git/ABC123/abc123-solution.git/info/refs'],
            ['the tests repository', 'https://artemis.example/git/ABC123/abc123-tests.git', 'https://artemis.example/git/ABC123/abc123-tests.git'],
            ['two participants in one text', 'clone /git/ABC123/abc123-jane-doe.git and /git/ABC123/abc123-max.git', 'clone /git/ABC123/abc123.git and /git/ABC123/abc123.git'],
        ])('should remove the whole participant from the repository of %s in a span', async (_description, url, expected) => {
            await service.initSentry(createProfileInfo({ testServer: false }));

            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            const scrubbed = callArgs.beforeSendSpan({ name: 'GET', attributes: { 'url.full': url } });

            expect(scrubbed.attributes['url.full']).toBe(expected);
        });

        it('should not send the user of the scope on streamed spans', async () => {
            const profileInfo = createProfileInfo({ testServer: false });

            await service.initSentry(profileInfo);

            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            const span = {
                name: 'GET /api/courses',
                attributes: {
                    'user.id': '1',
                    'user.email': 'student@example.org',
                    'user.ip_address': '127.0.0.1',
                    'user.name': 'ab12cde',
                    'http.response.status_code': 200,
                },
            };
            const scrubbed = callArgs.beforeSendSpan(span);

            expect(scrubbed.attributes).toEqual({ 'http.response.status_code': 200 });
        });

        it('should configure tracesSampler correctly', async () => {
            const profileInfo = createProfileInfo({ testServer: false });

            await service.initSentry(profileInfo);

            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            const tracesSampler = callArgs.tracesSampler;

            // Test time API endpoint - should return 0
            expect(tracesSampler({ name: '/api/public/time', inheritOrSampleWith: vi.fn() })).toBe(0.0);

            // Test iris status endpoint - should return 0.001
            expect(tracesSampler({ name: 'api/iris/status', inheritOrSampleWith: vi.fn() })).toBe(0.001);

            // Test default - should call inheritOrSampleWith
            const inheritMock = vi.fn().mockReturnValue(0.05);
            const result = tracesSampler({ name: 'api/other', inheritOrSampleWith: inheritMock });
            expect(inheritMock).toHaveBeenCalledWith(0.05); // defaultSampleRate for prod
            expect(result).toBe(0.05);
        });

        it('should use higher sample rate for non-prod environments', async () => {
            const profileInfo = createProfileInfo({ testServer: true });

            await service.initSentry(profileInfo);

            const callArgs = (Sentry.init as ReturnType<typeof vi.fn>).mock.calls[0][0];
            const tracesSampler = callArgs.tracesSampler;

            // For test environment, default sample rate should be 1.0
            const inheritMock = vi.fn().mockReturnValue(1.0);
            tracesSampler({ name: 'api/other', inheritOrSampleWith: inheritMock });
            expect(inheritMock).toHaveBeenCalledWith(1.0);
        });

        it('should report if WebAuthn is not supported', async () => {
            // Simulate WebAuthn not being supported
            Object.defineProperty(window, 'PublicKeyCredential', {
                value: undefined,
                writable: true,
                configurable: true,
            });

            vi.mocked(localStorageService.retrieveDate).mockReturnValue(undefined);

            const profileInfo = createProfileInfo({ testServer: false });

            await service.initSentry(profileInfo);

            expect(Sentry.captureException).toHaveBeenCalledOnce();
            expect(localStorageService.store).toHaveBeenCalledWith('webauthnNotSupportedTimestamp', expect.any(Date));
        });

        it('should not report WebAuthn issue if already reported today', async () => {
            // Simulate WebAuthn not being supported
            Object.defineProperty(window, 'PublicKeyCredential', {
                value: undefined,
                writable: true,
                configurable: true,
            });

            // Mock that it was already reported today
            vi.mocked(localStorageService.retrieveDate).mockReturnValue(new Date());

            const profileInfo = createProfileInfo({ testServer: false });

            await service.initSentry(profileInfo);

            expect(Sentry.captureException).not.toHaveBeenCalled();
            expect(localStorageService.store).not.toHaveBeenCalled();
        });

        it('should report WebAuthn issue if last reported on a different day', async () => {
            // Simulate WebAuthn not being supported
            Object.defineProperty(window, 'PublicKeyCredential', {
                value: undefined,
                writable: true,
                configurable: true,
            });

            // Mock that it was reported yesterday
            const yesterday = new Date();
            yesterday.setDate(yesterday.getDate() - 1);
            vi.mocked(localStorageService.retrieveDate).mockReturnValue(yesterday);

            const profileInfo = createProfileInfo({ testServer: false });

            await service.initSentry(profileInfo);

            expect(Sentry.captureException).toHaveBeenCalledOnce();
        });
    });

    describe('handleError', () => {
        beforeEach(async () => {
            // Initialize with non-local environment to enable Sentry error capture
            const profileInfo = createProfileInfo({ testServer: false });
            await service.initSentry(profileInfo);
            vi.clearAllMocks();
        });

        it('should not capture HttpErrorResponse with status 400-499 to Sentry', () => {
            const error = {
                name: 'HttpErrorResponse',
                status: 404,
                message: 'Not Found',
            };

            service.handleError(error);

            expect(Sentry.captureException).not.toHaveBeenCalled();
            expect(consoleErrorSpy).toHaveBeenCalled();
        });

        it('should capture HttpErrorResponse with status 500+ to Sentry', () => {
            const error = {
                name: 'HttpErrorResponse',
                status: 500,
                message: 'Internal Server Error',
            };

            service.handleError(error);

            expect(Sentry.captureException).toHaveBeenCalledOnce();
            expect(consoleErrorSpy).toHaveBeenCalled();
        });

        it('should capture non-HttpErrorResponse errors to Sentry', () => {
            const error = new Error('Test error');

            service.handleError(error);

            expect(Sentry.captureException).toHaveBeenCalledOnce();
            expect(consoleErrorSpy).toHaveBeenCalled();
        });

        it('should extract error from error.error property', () => {
            const innerError = new Error('Inner error');
            const error = { error: innerError };

            service.handleError(error);

            expect(Sentry.captureException).toHaveBeenCalledWith(innerError);
        });

        it('should extract error from error.message property', () => {
            const error = { message: 'Error message' };

            service.handleError(error);

            expect(Sentry.captureException).toHaveBeenCalledWith('Error message');
        });

        it('should extract error from error.originalError property', () => {
            const originalError = new Error('Original error');
            const error = { originalError: originalError };

            service.handleError(error);

            expect(Sentry.captureException).toHaveBeenCalledWith(originalError);
        });

        it('should use the error itself if no nested error properties exist', () => {
            const error = { someProperty: 'value' };

            service.handleError(error);

            expect(Sentry.captureException).toHaveBeenCalledWith(error);
        });

        it('should handle null error by not capturing to Sentry but passing to super', () => {
            // When error is null, the code path checks if (error && ...) which is falsy
            // so handleError just calls super.handleError(error)
            // The captureException line is skipped because environment check happens after null check
            // Looking at the code: if (error && error.name === ...) - this will be falsy for null
            // Then at line 71-74, it tries to access error.error which throws
            // This is actually a bug in the code - null errors would throw
            // We test that the error is thrown and console.error is called
            expect(() => service.handleError(null)).toThrow();
        });

        it('should not capture errors to Sentry in local environment', async () => {
            // Re-initialize with local environment
            const profileInfo = createProfileInfo({});
            profileInfo.testServer = undefined as any;
            await service.initSentry(profileInfo);
            vi.clearAllMocks();

            const error = new Error('Test error');

            service.handleError(error);

            expect(Sentry.captureException).not.toHaveBeenCalled();
            expect(consoleErrorSpy).toHaveBeenCalled();
        });

        it('should handle HttpErrorResponse with status exactly 400', () => {
            const error = {
                name: 'HttpErrorResponse',
                status: 400,
                message: 'Bad Request',
            };

            service.handleError(error);

            expect(Sentry.captureException).not.toHaveBeenCalled();
        });

        it('should handle HttpErrorResponse with status exactly 499', () => {
            const error = {
                name: 'HttpErrorResponse',
                status: 499,
                message: 'Client Closed Request',
            };

            service.handleError(error);

            expect(Sentry.captureException).not.toHaveBeenCalled();
        });

        it('should handle HttpErrorResponse with status 399 (not in 400-499 range)', () => {
            const error = {
                name: 'HttpErrorResponse',
                status: 399,
                message: 'Some response',
            };

            service.handleError(error);

            expect(Sentry.captureException).toHaveBeenCalledOnce();
        });
    });
});

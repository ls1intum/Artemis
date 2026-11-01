import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { ClientErrorHandler } from 'app/core/error-reporting/client-error.handler';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

vi.mock('app/core/error-reporting/capture-exception', () => ({ captureException: vi.fn() }));

describe('ClientErrorHandler', () => {
    let handler: ClientErrorHandler;
    let consoleError: ReturnType<typeof vi.spyOn>;
    let retrieveDate: ReturnType<typeof vi.fn>;
    let store: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        vi.mocked(captureException).mockClear();
        // Angular's default handling logs to the console, which is expected here
        consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        retrieveDate = vi.fn();
        store = vi.fn();
        TestBed.configureTestingModule({ providers: [ClientErrorHandler, { provide: LocalStorageService, useValue: { retrieveDate, store } }] });
        handler = TestBed.inject(ClientErrorHandler);
    });

    afterEach(() => {
        consoleError.mockRestore();
        vi.unstubAllGlobals();
    });

    describe('handleError', () => {
        it('should report an unhandled error and still log it to the console', () => {
            const error = new Error('Cannot read properties of undefined');

            handler.handleError(error);

            expect(captureException).toHaveBeenCalledExactlyOnceWith(error);
            expect(consoleError).toHaveBeenCalled();
        });

        it('should report the error that a wrapper contains', () => {
            const inner = new Error('inner');

            handler.handleError({ error: inner });

            expect(captureException).toHaveBeenCalledExactlyOnceWith(inner);
        });

        it('should report the original error of a wrapper', () => {
            const original = new Error('original');

            handler.handleError({ originalError: original, message: 'wrapper message' });

            expect(captureException).toHaveBeenCalledExactlyOnceWith(original);
        });

        it('should report the message of a wrapper that contains no error', () => {
            handler.handleError({ message: 'wrapper message' });

            expect(captureException).toHaveBeenCalledExactlyOnceWith('wrapper message');
        });

        it('should report the text of a failed request, not its response body', () => {
            const error = new HttpErrorResponse({ status: 503, url: '/api/core/courses', error: { stack: 'trace of the server that must not be reported' } });

            handler.handleError(error);

            expect(captureException).toHaveBeenCalledExactlyOnceWith(error.message);
        });

        it('should report the error itself if it has nothing to unwrap', () => {
            handler.handleError('a plain string');

            expect(captureException).toHaveBeenCalledExactlyOnceWith('a plain string');
        });

        it('should not fail on an error that is null', () => {
            expect(() => handler.handleError(null)).not.toThrow();

            expect(captureException).toHaveBeenCalledExactlyOnceWith(null);
        });

        it.each([400, 401, 403, 404, 409, 499])('should not report a failed request with status %s, because the user is told where it happens', (status) => {
            handler.handleError(new HttpErrorResponse({ status, url: '/api/core/courses' }));

            expect(captureException).not.toHaveBeenCalled();
            expect(consoleError).toHaveBeenCalled();
        });

        it.each([500, 502, 503, 0])('should report a failed request with status %s', (status) => {
            handler.handleError(new HttpErrorResponse({ status, url: '/api/core/courses' }));

            expect(captureException).toHaveBeenCalledTimes(1);
        });

        it('should recognise a failed request that was reconstructed and is no HttpErrorResponse any more', () => {
            handler.handleError({ name: 'HttpErrorResponse', status: 404, message: 'Not Found' });

            expect(captureException).not.toHaveBeenCalled();
        });
    });

    describe('reportIfPasskeyIsNotSupported', () => {
        const stubPasskeySupport = (supported: boolean) => vi.stubGlobal('PublicKeyCredential', supported ? class {} : undefined);

        it('should report nothing if the browser supports passkeys', () => {
            stubPasskeySupport(true);

            handler.reportIfPasskeyIsNotSupported();

            expect(captureException).not.toHaveBeenCalled();
            expect(store).not.toHaveBeenCalled();
        });

        it('should report a browser without passkey support once and remember it', () => {
            stubPasskeySupport(false);
            retrieveDate.mockReturnValue(undefined);

            handler.reportIfPasskeyIsNotSupported();

            expect(captureException).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ message: expect.stringContaining('WebAuthn') }), {
                severity: 'INFO',
                tags: { feature: 'Passkey Authentication' },
            });
            expect(store).toHaveBeenCalledExactlyOnceWith('webauthnNotSupportedTimestamp', expect.any(Date));
        });

        it('should not report again on the same day', () => {
            stubPasskeySupport(false);
            retrieveDate.mockReturnValue(new Date());

            handler.reportIfPasskeyIsNotSupported();

            expect(captureException).not.toHaveBeenCalled();
            expect(store).not.toHaveBeenCalled();
        });

        it('should report again on the next day', () => {
            stubPasskeySupport(false);
            const yesterday = new Date();
            yesterday.setDate(yesterday.getDate() - 1);
            retrieveDate.mockReturnValue(yesterday);

            handler.reportIfPasskeyIsNotSupported();

            expect(captureException).toHaveBeenCalledOnce();
            expect(store).toHaveBeenCalledOnce();
        });
    });
});

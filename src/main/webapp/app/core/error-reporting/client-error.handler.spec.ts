import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
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

        it('should hand a failed request to the reporter as it is, which leaves out the query of its url and the body of its response', () => {
            const error = new HttpErrorResponse({ status: 503, url: '/api/core/courses?token=secret', error: { stack: 'trace of the server that must not be reported' } });

            handler.handleError(error);

            expect(captureException).toHaveBeenCalledExactlyOnceWith(error);
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

        describe('errors that no change of the application can avoid', () => {
            it.each(['ResizeObserver loop completed with undelivered notifications.', 'ResizeObserver loop limit exceeded'])(
                'should not report the notice of the browser "%s", but still log it to the console',
                (message) => {
                    handler.handleError(new Error(message, { cause: { isTrusted: true } }));

                    expect(captureException).not.toHaveBeenCalled();
                    expect(consoleError).toHaveBeenCalled();
                },
            );

            it('should not report the notice of the browser when it comes as a plain message', () => {
                handler.handleError('ResizeObserver loop completed with undelivered notifications.');

                expect(captureException).not.toHaveBeenCalled();
            });

            it('should report a plain message that only mentions the resize observer', () => {
                handler.handleError('The ResizeObserver loop of the chart failed');

                expect(captureException).toHaveBeenCalledExactlyOnceWith('The ResizeObserver loop of the chart failed');
            });

            it('should report an error whose message only mentions the resize observer', () => {
                const error = new Error('Could not set up the ResizeObserver loop of the chart');

                handler.handleError(error);

                expect(captureException).toHaveBeenCalledExactlyOnceWith(error);
            });

            it.each([
                [
                    'V8',
                    "TypeError: Cannot read properties of undefined (reading 'M_ID')\n    at Y (chrome-extension://eppiocemhmnlbhjplcgkofciiegomcon/executors/200.js:1:761)\n    at E (chrome-extension://eppiocemhmnlbhjplcgkofciiegomcon/executors/200.js:1:1442)",
                ],
                [
                    'Firefox',
                    'TypeError: undefined has no properties\nrun@moz-extension://3c2f7d9a-0000-4000-8000-000000000000/content.js:12:5\n@https://artemis.tum.de/main.js:1:1',
                ],
                ['Safari', 'TypeError: undefined is not an object\nrun@safari-web-extension://ABC/content.js:12:5\nglobal code@https://artemis.tum.de/sign-in:1:30'],
            ])('should not report an error that was thrown inside a browser extension (%s)', (_browser, stack) => {
                const error = new Error('thrown by an extension');
                error.stack = stack;

                handler.handleError(error);

                expect(captureException).not.toHaveBeenCalled();
            });

            it('should report an error of the application that a browser extension caused, because only the top frame decides', () => {
                const error = new Error('Cannot read properties of undefined');
                error.stack =
                    "TypeError: Cannot read properties of undefined (reading 'toLowerCase')\n    at isToggleShortcut (https://artemis.tum.de/main.js:17216:22)\n    at dispatch (chrome-extension://abc/content.js:1:1)";

                handler.handleError(error);

                expect(captureException).toHaveBeenCalledExactlyOnceWith(error);
            });

            it('should report an error whose message mentions an extension url but whose stack is ours', () => {
                const error = new Error('Failed to load chrome-extension://abc/icon.png');
                error.stack = 'Error: Failed to load chrome-extension://abc/icon.png\n    at load (https://artemis.tum.de/main.js:1:1)';

                handler.handleError(error);

                expect(captureException).toHaveBeenCalledExactlyOnceWith(error);
            });

            it('should report an error without a stack', () => {
                const error = new Error('no stack');
                error.stack = undefined;

                handler.handleError(error);

                expect(captureException).toHaveBeenCalledExactlyOnceWith(error);
            });

            it('should report an address that matches no route as information only while the application starts', () => {
                const error = new Error("NG04002: 'courses/588/excercises'");

                handler.handleError(error);

                expect(captureException).toHaveBeenCalledExactlyOnceWith(error, { severity: 'INFO' });
            });

            it('should report an address that matches no route as an error once the application has navigated, because a link of the application is broken then', () => {
                TestBed.inject(Router).navigated = true;
                const error = new Error("NG04002: 'courses/588/exercises/nowhere'");

                handler.handleError(error);

                expect(captureException).toHaveBeenCalledExactlyOnceWith(error);
            });

            it('should not fail on an error whose stack is no text', () => {
                const error = new Error('odd');
                (error as { stack: unknown }).stack = 42;

                expect(() => handler.handleError(error)).not.toThrow();

                expect(captureException).toHaveBeenCalledExactlyOnceWith(error);
            });
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

        it('should not stop the application from starting if the browser blocks its storage', () => {
            stubPasskeySupport(false);
            retrieveDate.mockImplementation(() => {
                throw new DOMException('blocked', 'SecurityError');
            });

            expect(() => handler.reportIfPasskeyIsNotSupported()).not.toThrow();

            // without the marker the report would be sent on every start, so it is not sent
            expect(captureException).not.toHaveBeenCalled();
        });

        it('should not stop the application from starting if the storage is full, and not report', () => {
            stubPasskeySupport(false);
            retrieveDate.mockReturnValue(undefined);
            store.mockImplementation(() => {
                throw new DOMException('full', 'QuotaExceededError');
            });

            expect(() => handler.reportIfPasskeyIsNotSupported()).not.toThrow();

            expect(captureException).not.toHaveBeenCalled();
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

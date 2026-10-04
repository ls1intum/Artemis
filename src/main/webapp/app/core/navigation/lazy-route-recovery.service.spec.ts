import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { LAZY_ROUTE_RECOVERY_KEY_PREFIX, LazyRouteRecoveryService } from 'app/core/navigation/lazy-route-recovery.service';

describe('LazyRouteRecoveryService', () => {
    let service: LazyRouteRecoveryService;
    let assign: ReturnType<typeof vi.fn>;
    let addAlert: ReturnType<typeof vi.fn>;
    let handleError: ReturnType<typeof vi.fn>;
    let store: Map<string, string>;

    const FAILED_URL = '/course-management/1/programming-exercises/new';
    /** What Chrome reports when a lazily imported route chunk cannot be fetched. */
    const chunkError = () => new Error('Failed to fetch dynamically imported module: https://artemis.example.org/chunk-ABC123.js');

    beforeEach(() => {
        assign = vi.fn();
        addAlert = vi.fn();
        handleError = vi.fn();
        store = new Map<string, string>();

        const windowStub = {
            location: { assign },
            sessionStorage: {
                getItem: (key: string) => store.get(key) ?? null,
                setItem: (key: string, value: string) => void store.set(key, value),
            },
        };

        TestBed.configureTestingModule({
            providers: [
                LazyRouteRecoveryService,
                { provide: WINDOW_INJECTOR_TOKEN, useValue: windowStub },
                { provide: AlertService, useValue: { addAlert } },
                { provide: SentryErrorHandler, useValue: { handleError } },
            ],
        });

        service = TestBed.inject(LazyRouteRecoveryService);
    });

    it('should reload into the requested url when a route chunk fails to load', () => {
        service.handleNavigationError(chunkError(), FAILED_URL);

        expect(assign).toHaveBeenCalledExactlyOnceWith(FAILED_URL);
        expect(addAlert).not.toHaveBeenCalled();
    });

    it('should alert instead of reloading again when the same url already failed once', () => {
        service.handleNavigationError(chunkError(), FAILED_URL);
        assign.mockClear();

        service.handleNavigationError(chunkError(), FAILED_URL);

        expect(assign).not.toHaveBeenCalled();
        expect(addAlert).toHaveBeenCalledOnce();
        expect(addAlert.mock.calls[0][0]).toMatchObject({ type: AlertType.DANGER, message: 'artemisApp.lazyRouteLoadFailedAlert' });
    });

    it('should still recover a different url that failed for the first time', () => {
        service.handleNavigationError(chunkError(), FAILED_URL);
        assign.mockClear();

        service.handleNavigationError(chunkError(), '/course-management/1/text-exercises/new');

        expect(assign).toHaveBeenCalledExactlyOnceWith('/course-management/1/text-exercises/new');
    });

    it('should offer an action that reloads the page', () => {
        service.handleNavigationError(chunkError(), FAILED_URL);
        service.handleNavigationError(chunkError(), FAILED_URL);
        assign.mockClear();

        addAlert.mock.calls[0][0].action.callback();

        expect(assign).toHaveBeenCalledExactlyOnceWith(FAILED_URL);
    });

    it.each(['error loading dynamically imported module: https://artemis.example.org/chunk.js', 'Importing a module script failed.', 'Loading chunk 42 failed.'])(
        'should recognise "%s" as a chunk load failure',
        (message: string) => {
            service.handleNavigationError(new Error(message), FAILED_URL);

            expect(assign).toHaveBeenCalledOnce();
        },
    );

    // Bundlers that wrap the failure give it a name rather than one of the messages above.
    it('should recognise an error named ChunkLoadError', () => {
        const error = new Error('Loading failed for the chunk');
        error.name = 'ChunkLoadError';

        service.handleNavigationError(error, FAILED_URL);

        expect(assign).toHaveBeenCalledOnce();
    });

    it('should ignore an error that is not a chunk load failure', () => {
        service.handleNavigationError(new Error('Cannot activate the route because the guard rejected it'), FAILED_URL);

        expect(assign).not.toHaveBeenCalled();
        expect(addAlert).not.toHaveBeenCalled();
    });

    it('should ignore a server error surfaced by a resolver', () => {
        service.handleNavigationError({ status: 500, message: 'Internal Server Error' }, FAILED_URL);

        expect(assign).not.toHaveBeenCalled();
        expect(addAlert).not.toHaveBeenCalled();
    });

    // A reload can only be limited to one attempt while the marker is readable afterwards. Where it is not, every
    // fresh document would see the same url fail for the "first" time, so reloading at all would never terminate.
    describe('when the recovery attempt cannot be recorded', () => {
        function configureWithStorage(sessionStorage: Partial<Storage> | (() => never)) {
            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [
                    LazyRouteRecoveryService,
                    {
                        provide: WINDOW_INJECTOR_TOKEN,
                        useValue: {
                            location: { assign },
                            get sessionStorage(): Partial<Storage> {
                                return typeof sessionStorage === 'function' ? sessionStorage() : sessionStorage;
                            },
                        },
                    },
                    { provide: AlertService, useValue: { addAlert } },
                    { provide: SentryErrorHandler, useValue: { handleError } },
                ],
            });
            return TestBed.inject(LazyRouteRecoveryService);
        }

        it('should alert rather than reload when session storage throws', () => {
            // A browser configured to block site data throws on access rather than returning null.
            const service = configureWithStorage(() => {
                throw new Error('The operation is insecure.');
            });

            service.handleNavigationError(chunkError(), FAILED_URL);

            expect(assign).not.toHaveBeenCalled();
            expect(addAlert).toHaveBeenCalledOnce();
        });

        it('should alert rather than reload when session storage silently drops the write', () => {
            const service = configureWithStorage({ getItem: () => null, setItem: () => {} });

            service.handleNavigationError(chunkError(), FAILED_URL);

            expect(assign).not.toHaveBeenCalled();
            expect(addAlert).toHaveBeenCalledOnce();
        });
    });

    it('should report the failure so it is visible in monitoring', () => {
        const error = chunkError();

        service.handleNavigationError(error, FAILED_URL);

        expect(handleError).toHaveBeenCalledExactlyOnceWith(error);
    });

    it('should not report an error it does not act on', () => {
        service.handleNavigationError(new Error('Cannot activate the route because the guard rejected it'), FAILED_URL);

        expect(handleError).not.toHaveBeenCalled();
    });

    it('should remember the attempt under a namespaced key so it survives the reload', () => {
        service.handleNavigationError(chunkError(), FAILED_URL);

        expect([...store.keys()]).toEqual([`${LAZY_ROUTE_RECOVERY_KEY_PREFIX}${FAILED_URL}`]);
    });
});

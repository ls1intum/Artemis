import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { EMPTY, Subject, firstValueFrom, of } from 'rxjs';
import { ArtemisVersionInterceptor, WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpRequest, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SwUpdate } from '@angular/service-worker';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';

vi.mock('app/app.constants', async (importOriginal) => ({ ...(await importOriginal<typeof import('app/app.constants')>()), VERSION: '10.1.0' }));

describe(`ArtemisVersionInterceptor`, () => {
    let alertService: AlertService;
    let swUpdate: any;
    let checkForUpdateSpy: any;
    let activateUpdateSpy: any;
    let unrecoverable: Subject<unknown>;

    beforeAll(() => {
        vi.useFakeTimers();
    });

    afterAll(() => {
        vi.useRealTimers();
    });

    beforeEach(() => {
        unrecoverable = new Subject<unknown>();
        swUpdate = {
            isEnabled: true,
            activated: EMPTY,
            available: EMPTY,
            unrecoverable,
            checkForUpdate: () => Promise.resolve(true),
            activateUpdate: () => Promise.resolve(true),
        };
        checkForUpdateSpy = vi.spyOn(swUpdate, 'checkForUpdate');
        activateUpdateSpy = vi.spyOn(swUpdate, 'activateUpdate');

        TestBed.configureTestingModule({
            providers: [
                ArtemisVersionInterceptor,
                { provide: SwUpdate, useValue: swUpdate },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: WINDOW_INJECTOR_TOKEN,
                    useValue: {
                        location: {
                            reload: vi.fn(),
                        },
                    },
                },
            ],
        });

        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should check for an update immediately and after 60 seconds again if app is stable', async () => {
        TestBed.inject(ArtemisVersionInterceptor);
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();
        vi.advanceTimersByTime(60000);
        expect(checkForUpdateSpy).toHaveBeenCalledTimes(2);
        vi.clearAllTimers();
    });

    it('should check for an update after 30s if app is not stable', async () => {
        const isStableSubject = new Subject<boolean>();
        const appRef = TestBed.inject(ApplicationRef);
        vi.spyOn(appRef, 'isStable', 'get').mockReturnValue(isStableSubject.asObservable());
        TestBed.inject(ArtemisVersionInterceptor);
        expect(checkForUpdateSpy).not.toHaveBeenCalled();
        vi.advanceTimersByTime(30000);
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();
        vi.clearAllTimers();
    });

    it('should show the update alert and have functional callback', async () => {
        const funMock = vi.fn();
        const addAlertSpy = vi.spyOn(alertService, 'addAlert').mockImplementation(funMock);
        // TODO: mock the injected services in ArtemisVersionInterceptor
        TestBed.inject(ArtemisVersionInterceptor);
        await Promise.resolve();
        await Promise.resolve();
        expect(addAlertSpy).toHaveBeenCalledOnce();
        expect(funMock).toHaveBeenCalledOnce();
        expect(funMock).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.INFO, message: 'artemisApp.outdatedAlert' }));

        expect(activateUpdateSpy).not.toHaveBeenCalled();
        funMock.mock.calls[0][0].action.callback();
        expect(activateUpdateSpy).toHaveBeenCalledOnce();
        vi.clearAllTimers();
    });

    it('should tell the worker to look for updates in HTTP requests (only) if the version is not equal to current', async () => {
        const requestMock = new HttpRequest('GET', '/test');

        const intercept = TestBed.inject(ArtemisVersionInterceptor);
        await Promise.resolve();
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();

        let mockHandler = {
            handle: vi.fn(() => of(new HttpResponse({ status: 200, body: {}, headers: new HttpHeaders({ [ARTEMIS_VERSION_HEADER]: VERSION }) }))),
        };
        await firstValueFrom(intercept.intercept(requestMock, mockHandler));
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();

        mockHandler = {
            handle: vi.fn(() => of(new HttpResponse({ status: 200, body: {}, headers: new HttpHeaders({ [ARTEMIS_VERSION_HEADER]: 'x.y.z' }) }))),
        };
        await firstValueFrom(intercept.intercept(requestMock, mockHandler));
        expect(checkForUpdateSpy).toHaveBeenCalledTimes(2);
        vi.clearAllTimers();
    });

    describe('outdated detection when the service worker reports no update', () => {
        const requestMock = new HttpRequest('GET', '/test');
        const responseWithVersion = (version: string) => ({
            handle: vi.fn(() => of(new HttpResponse({ status: 200, body: {}, headers: new HttpHeaders({ [ARTEMIS_VERSION_HEADER]: version }) }))),
        });

        it('should show the update alert if the server reports a newer version', async () => {
            checkForUpdateSpy.mockResolvedValue(false);
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            const intercept = TestBed.inject(ArtemisVersionInterceptor);
            await Promise.resolve();
            expect(addAlertSpy).not.toHaveBeenCalled();

            await firstValueFrom(intercept.intercept(requestMock, responseWithVersion('10.2.0')));
            await Promise.resolve();
            await Promise.resolve();

            expect(addAlertSpy).toHaveBeenCalledOnce();
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ message: 'artemisApp.outdatedAlert' }));
            vi.clearAllTimers();
        });

        it('should show the update alert if the service worker check fails and the server reports a newer version', async () => {
            checkForUpdateSpy.mockResolvedValueOnce(false).mockRejectedValue(new Error('service worker unavailable'));
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            const intercept = TestBed.inject(ArtemisVersionInterceptor);
            await Promise.resolve();

            await firstValueFrom(intercept.intercept(requestMock, responseWithVersion('10.2.0')));
            await Promise.resolve();
            await Promise.resolve();

            expect(addAlertSpy).toHaveBeenCalledOnce();
            vi.clearAllTimers();
        });

        it('should not show the update alert if the server reports an older version', async () => {
            checkForUpdateSpy.mockResolvedValue(false);
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            const intercept = TestBed.inject(ArtemisVersionInterceptor);
            await Promise.resolve();

            await firstValueFrom(intercept.intercept(requestMock, responseWithVersion('10.0.2')));
            await Promise.resolve();
            await Promise.resolve();

            expect(checkForUpdateSpy).toHaveBeenCalledTimes(2);
            expect(addAlertSpy).not.toHaveBeenCalled();
            vi.clearAllTimers();
        });

        it('should show the update alert if the service worker reaches an unrecoverable state', async () => {
            checkForUpdateSpy.mockResolvedValue(false);
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            TestBed.inject(ArtemisVersionInterceptor);
            await Promise.resolve();
            expect(addAlertSpy).not.toHaveBeenCalled();

            unrecoverable.next({ type: 'UNRECOVERABLE_STATE', reason: 'missing asset' });
            await Promise.resolve();
            await Promise.resolve();

            expect(addAlertSpy).toHaveBeenCalledOnce();
            vi.clearAllTimers();
        });
    });
});

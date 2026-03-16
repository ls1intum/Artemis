import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { EMPTY, Subject, firstValueFrom, of } from 'rxjs';
import { ArtemisVersionInterceptor, WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpRequest, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SwUpdate } from '@angular/service-worker';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';

describe(`ArtemisVersionInterceptor`, () => {
    setupTestBed({ zoneless: true });

    let alertService: AlertService;
    let swUpdate: any;
    let checkForUpdateSpy: any;
    let activateUpdateSpy: any;

    beforeAll(() => {
        vi.useFakeTimers();
    });

    afterAll(() => {
        vi.useRealTimers();
    });

    beforeEach(() => {
        swUpdate = {
            isEnabled: true,
            activated: EMPTY,
            available: EMPTY,
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
});

import { EMPTY, Subject, of } from 'rxjs';
import { ArtemisVersionInterceptor, WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { HttpHeaders, HttpRequest, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { SwUpdate } from '@angular/service-worker';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';

describe(`ArtemisVersionInterceptor`, () => {
    let alertService: AlertService;
    let swUpdate: any;
    let checkForUpdateSpy: any;
    let activateUpdateSpy: any;

    beforeAll(() => {
        jest.useFakeTimers();
    });

    afterAll(() => {
        jest.useRealTimers();
    });

    beforeEach(() => {
        swUpdate = {
            isEnabled: true,
            activated: EMPTY,
            available: EMPTY,
            checkForUpdate: () => Promise.resolve(true),
            activateUpdate: () => Promise.resolve(true),
        };
        checkForUpdateSpy = jest.spyOn(swUpdate, 'checkForUpdate');
        activateUpdateSpy = jest.spyOn(swUpdate, 'activateUpdate');

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
                            reload: jest.fn(),
                        },
                    },
                },
            ],
        });

        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should check for an update immediately and after 60 seconds again if app is stable', fakeAsync(() => {
        TestBed.inject(ArtemisVersionInterceptor);
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();
        tick(60000);
        expect(checkForUpdateSpy).toHaveBeenCalledTimes(2);
        discardPeriodicTasks();
    }));

    it('should check for an update after 30s if app is not stable', fakeAsync(() => {
        const isStableSubject = new Subject<boolean>();
        const appRef = TestBed.inject(ApplicationRef);
        (appRef as any).isStable = isStableSubject.asObservable();
        TestBed.inject(ArtemisVersionInterceptor);
        isStableSubject.next(false);
        expect(checkForUpdateSpy).not.toHaveBeenCalled();
        tick(30000);
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();
        discardPeriodicTasks();
    }));

    it('should show the update alert and have functional callback', fakeAsync(() => {
        const funMock = jest.fn();
        const addAlertSpy = jest.spyOn(alertService, 'addAlert').mockImplementation(funMock);
        // TODO: mock the injected services in ArtemisVersionInterceptor
        TestBed.inject(ArtemisVersionInterceptor);
        tick();
        expect(addAlertSpy).toHaveBeenCalledOnce();
        expect(funMock).toHaveBeenCalledOnce();
        expect(funMock).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.INFO, message: 'artemisApp.outdatedAlert' }));

        expect(activateUpdateSpy).not.toHaveBeenCalled();
        funMock.mock.calls[0][0].action.callback();
        expect(activateUpdateSpy).toHaveBeenCalledOnce();
        discardPeriodicTasks();
    }));

    it('should tell the worker to look for updates in HTTP requests (only) if the version is not equal to current', fakeAsync(() => {
        const requestMock = new HttpRequest('GET', '/test');

        const intercept = TestBed.inject(ArtemisVersionInterceptor);
        tick();
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();

        let mockHandler = {
            handle: jest.fn(() => of(new HttpResponse({ status: 200, body: {}, headers: new HttpHeaders({ [ARTEMIS_VERSION_HEADER]: VERSION }) }))),
        };
        intercept.intercept(requestMock, mockHandler).subscribe();
        tick();
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();

        mockHandler = {
            handle: jest.fn(() => of(new HttpResponse({ status: 200, body: {}, headers: new HttpHeaders({ [ARTEMIS_VERSION_HEADER]: 'x.y.z' }) }))),
        };
        intercept.intercept(requestMock, mockHandler).subscribe();
        expect(checkForUpdateSpy).toHaveBeenCalledTimes(2);
        discardPeriodicTasks();
    }));
});

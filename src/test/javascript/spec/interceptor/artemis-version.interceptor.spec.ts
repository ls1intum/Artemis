import { EMPTY, Subject, of } from 'rxjs';
import { ServerDateService } from 'app/shared/server-date.service';
import { ArtemisVersionInterceptor } from 'app/core/interceptor/artemis-version.interceptor';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { MockService } from 'ng-mocks';
import { MockArtemisServerDateService } from '../helpers/mocks/service/mock-server-date.service';
import { discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { SwUpdate } from '@angular/service-worker';
import { ApplicationRef } from '@angular/core';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';

describe(`ArtemisVersionInterceptor`, () => {
    let appRef: ApplicationRef;
    let alertService: AlertService;
    let serverDateService: ServerDateService;

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

        appRef = { isStable: of(true) } as any as ApplicationRef;
        alertService = MockService(AlertService);
        serverDateService = new MockArtemisServerDateService();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should check for an update immediately and after 60 seconds again if app is stable', fakeAsync(() => {
        new ArtemisVersionInterceptor(appRef, swUpdate as any as SwUpdate, serverDateService, alertService, {} as any as Window);

        expect(checkForUpdateSpy).toHaveBeenCalledOnce();
        tick(60000);
        expect(checkForUpdateSpy).toHaveBeenCalledTimes(2);
        discardPeriodicTasks();
    }));

    it('should check for an update after 30s if app is not stable', fakeAsync(() => {
        const sub = new Subject<boolean>();
        new ArtemisVersionInterceptor({ isStable: sub.asObservable() } as any as ApplicationRef, swUpdate as any as SwUpdate, serverDateService, alertService, {} as any as Window);
        sub.next(false);
        expect(checkForUpdateSpy).not.toHaveBeenCalled();
        tick(30000);
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();
        discardPeriodicTasks();
    }));

    it('should show the update alert and have functional callback', fakeAsync(() => {
        const funMock = jest.fn();
        const addAlertSpy = jest.spyOn(alertService, 'addAlert').mockImplementation(funMock);
        new ArtemisVersionInterceptor(appRef, swUpdate as any as SwUpdate, serverDateService, alertService, { location: { reload: jest.fn() } } as any as Window);
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

        const intercept = new ArtemisVersionInterceptor(appRef, swUpdate as any as SwUpdate, serverDateService, alertService, {} as any as Window);
        tick();
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();

        let mockHandler = {
            handle: jest.fn(() => of(new HttpResponse({ status: 200, body: {}, headers: new HttpHeaders({ [ARTEMIS_VERSION_HEADER]: VERSION }) }))),
        };
        intercept.intercept(requestMock, mockHandler).subscribe();
        tick();
        expect(checkForUpdateSpy).toHaveBeenCalledOnce();

        mockHandler = {
            handle: jest.fn(() => of(new HttpResponse({ status: 200, body: {}, headers: new HttpHeaders({ [ARTEMIS_VERSION_HEADER]: '0.0.0' }) }))),
        };
        intercept.intercept(requestMock, mockHandler).subscribe();
        expect(checkForUpdateSpy).toHaveBeenCalledTimes(2);
        discardPeriodicTasks();
    }));
});

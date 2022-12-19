import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { TestBed, fakeAsync, flush, inject, tick } from '@angular/core/testing';
import { MissingTranslationHandler, TranslateModule, TranslateService } from '@ngx-translate/core';
import { missingTranslationHandler } from 'app/core/config/translation.config';
import { Alert, AlertCreationProperties, AlertService, AlertType } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';

describe('Alert Service Test', () => {
    const alertSample = {
        type: AlertType.SUCCESS,
        message: 'Hello Jhipster',
        timeout: 3000,
    };
    const alertSampleWithId = {
        type: AlertType.SUCCESS,
        message: 'Hello Jhipster',
        timeout: 3000,
    };
    let service: AlertService;
    let eventManager: EventManager;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot({
                    missingTranslationHandler: {
                        provide: MissingTranslationHandler,
                        useFactory: missingTranslationHandler,
                    },
                }),
            ],
        });
        const translateService = TestBed.inject(TranslateService);
        translateService.setDefaultLang('en');
        translateService.setTranslation('en', {
            'Hello Jhipster': 'Hello Jhipster',
            'Hello Jhipster success': 'Hello Jhipster success',
            'Hello Jhipster info': 'Hello Jhipster info',
            'Error Message': 'Error Message',
            'Second Error Message': 'Second Error Message',
            'Bad Request': 'Bad Request',
            'artemisApp.foo.minField': 'artemisApp.foo.minField',
        });
        service = TestBed.inject(AlertService);
        eventManager = TestBed.inject(EventManager);
        jest.useFakeTimers();
    });

    it('should produce a proper alert object and fetch it', () => {
        expect(service.addAlert(alertSample)).toEqual(expect.objectContaining(alertSampleWithId as Alert));
        expect(service.get()).toHaveLength(1);
        expect(service.get()[0]).toEqual(expect.objectContaining(alertSampleWithId as Alert));
    });

    it('should close an alert correctly', () => {
        const alert0 = service.addAlert({ type: AlertType.INFO, message: 'Hello Jhipster info', onClose: jest.fn() });
        const alert1 = service.addAlert({ type: AlertType.INFO, message: 'Hello Jhipster info 2', onClose: jest.fn() });
        const alert2 = service.addAlert({ type: AlertType.SUCCESS, message: 'Hello Jhipster success', onClose: jest.fn() });
        expect(alert2).toEqual(
            expect.objectContaining({
                type: AlertType.SUCCESS,
                message: 'Hello Jhipster success',
            } as Alert),
        );

        expect(service.get()).toHaveLength(3);
        alert1.close?.();
        expect(service.get()).toHaveLength(2);
        expect(service.get()[0]).toEqual(
            expect.objectContaining({
                type: AlertType.SUCCESS,
                message: 'Hello Jhipster success',
            } as Alert),
        );
        expect(alert1.onClose).toHaveBeenCalledOnce();
        alert2.close?.();
        expect(service.get()).toHaveLength(1);
        expect(service.get()[0]).toEqual(
            expect.objectContaining({
                type: AlertType.INFO,
                message: 'Hello Jhipster info',
            } as Alert),
        );
        expect(alert2.onClose).toHaveBeenCalledOnce();

        alert0.close?.();
        expect(service.get()).toHaveLength(0);
        expect(alert0.onClose).toHaveBeenCalledOnce();
    });

    it('should close an alert on timeout correctly', () => {
        const alert = { type: AlertType.INFO, message: 'Hello Jhipster info', onClose: jest.fn() } as AlertCreationProperties;
        service.addAlert(alert);

        expect(service.get()).toHaveLength(1);

        jest.advanceTimersByTime(16000);

        expect(service.get()).toHaveLength(0);
        expect(alert.onClose).toHaveBeenCalledOnce();
    });

    it('should clear alerts', () => {
        const alerts = [
            { type: AlertType.INFO, message: 'Hello Jhipster info1', onClose: jest.fn() },
            { type: AlertType.DANGER, message: 'Hello Jhipster info2', onClose: jest.fn() },
            { type: AlertType.SUCCESS, message: 'Hello Jhipster info3', onClose: jest.fn() },
        ] as AlertCreationProperties[];
        alerts.forEach((alert) => service.addAlert(alert));
        expect(service.get()).toHaveLength(3);
        service.closeAll();
        expect(service.get()).toHaveLength(0);
        alerts.forEach((alert) => expect(alert.onClose).toHaveBeenCalledOnce());
    });

    it('should produce a success message', () => {
        expect(service.addAlert({ type: AlertType.SUCCESS, message: 'Hello Jhipster' })).toEqual(
            expect.objectContaining({
                type: AlertType.SUCCESS,
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a error message', () => {
        expect(service.addAlert({ type: AlertType.DANGER, message: 'Hello Jhipster' })).toEqual(
            expect.objectContaining({
                type: AlertType.DANGER,
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a warning message', () => {
        expect(service.addAlert({ type: AlertType.WARNING, message: 'Hello Jhipster' })).toEqual(
            expect.objectContaining({
                type: AlertType.WARNING,
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a info message', () => {
        expect(service.addAlert({ type: AlertType.INFO, message: 'Hello Jhipster' })).toEqual(
            expect.objectContaining({
                type: AlertType.INFO,
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a info message with translated message if key exists', inject([TranslateService], (translateService: TranslateService) => {
        translateService.setTranslation('en', {
            'hello.jhipster': 'Translated message',
        });
        expect(service.addAlert({ type: AlertType.INFO, message: 'Hello Jhipster', translationKey: 'hello.jhipster' })).toEqual(
            expect.objectContaining({
                type: AlertType.INFO,
                message: 'Translated message',
            } as Alert),
        );
    }));

    it('should produce a info message with provided message if key does not exists', () => {
        expect(service.addAlert({ type: AlertType.INFO, message: 'Hello Jhipster', translationKey: 'hello.jhipster' })).toEqual(
            expect.objectContaining({
                type: AlertType.INFO,
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a info message with provided key if translation key does not exist in translations and message is not provided', () => {
        expect(service.addAlert({ type: AlertType.INFO, translationKey: 'hello.jhipster' })).toEqual(
            expect.objectContaining({
                type: AlertType.INFO,
                message: 'hello.jhipster',
            } as Alert),
        );
    });

    it('should display an alert on status 0', () => {
        // GIVEN
        eventManager.broadcast({ name: 'artemisApp.httpError', content: { status: 0 } });
        // THEN
        expect(service.get()).toHaveLength(1);
        expect(service.get()[0].message).toBe('Server not reachable');
    });

    it('should not display an alert on status 404', () => {
        // GIVEN
        eventManager.broadcast({ name: 'artemisApp.httpError', content: { status: 404 } });
        // THEN
        expect(service.get()).toHaveLength(0);
    });

    it('should display an alert on status 400 for generic error and use the error title', () => {
        // GIVEN
        const response = new HttpErrorResponse({
            url: 'http://localhost:8080/api/foos',
            headers: new HttpHeaders(),
            status: 400,
            statusText: 'Bad Request',
            error: {
                type: 'https://www.jhipster.tech/problem/constraint-violation',
                title: 'Bad Request: This is a default text from the server exception',
                status: 400,
                path: '/api/foos',
                message: 'error.validation',
            },
        });
        eventManager.broadcast({ name: 'artemisApp.httpError', content: response });
        // THEN
        expect(service.get()).toHaveLength(1);
        expect(service.get()[0].message).toBe('Bad Request: This is a default text from the server exception');
    });

    it('should not display an alert on status 400 for errors without message', () => {
        // GIVEN
        const response = new HttpErrorResponse({
            url: 'http://localhost:8080/api/foos',
            headers: new HttpHeaders(),
            status: 400,
            statusText: 'Bad request',
        });
        eventManager.broadcast({ name: 'artemisApp.httpError', content: response });
        // THEN
        expect(service.get()).toHaveLength(0);
    });

    it('should display an alert on status 400 for invalid parameters', () => {
        // GIVEN
        const response = new HttpErrorResponse({
            url: 'http://localhost:8080/api/foos',
            headers: new HttpHeaders(),
            status: 400,
            statusText: 'Bad Request',
            error: {
                type: 'https://www.jhipster.tech/problem/constraint-violation',
                title: 'Method argument not valid',
                status: 400,
                path: '/api/foos',
                message: 'error.validation',
                fieldErrors: [{ objectName: 'foo', field: 'minField', message: 'Min' }],
            },
        });
        eventManager.broadcast({ name: 'artemisApp.httpError', content: response });
        // THEN
        expect(service.get()).toHaveLength(1);
        expect(service.get()[0].message).toBe('Error on field &#34;artemisApp.foo.minField&#34;');
    });

    it('should display an alert on status 400 for error headers', () => {
        // GIVEN
        const response = new HttpErrorResponse({
            url: 'http://localhost:8080/api/foos',
            headers: new HttpHeaders().append('app-error', 'Error Message').append('app-params', 'foo'),
            status: 400,
            statusText: 'Bad Request',
            error: {
                status: 400,
                message: 'error.validation',
            },
        });
        eventManager.broadcast({ name: 'artemisApp.httpError', content: response });
        // THEN
        expect(service.get()).toHaveLength(1);
        expect(service.get()[0].message).toBe('Error Message');
    });

    it('should not show two alerts with the same content if spawned within 50ms', fakeAsync(() => {
        const initialAlert = service.addAlert({
            type: AlertType.DANGER,
            message: 'Test123',
        });
        expect(service.get()).toEqual([initialAlert]);

        tick(25);

        const secondAlert = service.addAlert({
            type: AlertType.DANGER,
            message: 'Test123',
        });

        // Check that it is actually the same object by reference
        expect(secondAlert).toBe(initialAlert);
        expect(service.get()).toEqual([initialAlert]);

        // After at least 50ms, the new alert should actually be added again
        tick(30);

        const thirdAlert = service.addAlert({
            type: AlertType.DANGER,
            message: 'Test123',
        });

        expect(thirdAlert).not.toBe(initialAlert);
        expect(service.get()).toEqual([thirdAlert, initialAlert]);

        flush();
    }));
});

import { inject, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService, MissingTranslationHandler } from '@ngx-translate/core';
import { missingTranslationHandler } from 'app/core/config/translation.config';
import { Alert, AlertService, AlertType } from 'app/core/util/alert.service';

describe('Alert Service Test', () => {
    let extAlerts: Alert[];
    const alertSample = {
        type: 'success' as AlertType,
        message: 'Hello Jhipster',
        timeout: 3000,
    };
    const alertSampleWithId = {
        type: 'success' as AlertType,
        message: 'Hello Jhipster',
        id: 0,
        timeout: 3000,
    };
    let service: AlertService;

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
        });
        service = TestBed.inject(AlertService);
        jest.useFakeTimers();
        extAlerts = [];
    });

    it('should produce a proper alert object and fetch it', () => {
        expect(service.addAlert(alertSample)).toEqual(expect.objectContaining(alertSampleWithId as Alert));
        expect(service.get().length).toBe(1);
        expect(service.get()[0]).toEqual(expect.objectContaining(alertSampleWithId as Alert));
    });

    it('should produce a proper alert object and add it to external alert objects array', () => {
        expect(service.addAlert(alertSample, extAlerts)).toEqual(expect.objectContaining(alertSampleWithId as Alert));
        expect(extAlerts.length).toBe(1);
        expect(extAlerts[0]).toEqual(expect.objectContaining(alertSampleWithId as Alert));
    });

    it('should produce an alert object with correct id', () => {
        service.addAlert({ type: 'info', message: 'Hello Jhipster info' });
        expect(service.addAlert({ type: 'success', message: 'Hello Jhipster success' })).toEqual(
            expect.objectContaining({
                type: 'success',
                message: 'Hello Jhipster success',
                id: 1,
            } as Alert),
        );

        expect(service.get().length).toBe(2);
        expect(service.get()[1]).toEqual(
            expect.objectContaining({
                type: 'success',
                message: 'Hello Jhipster success',
                id: 1,
            } as Alert),
        );
    });

    it('should close an alert correctly', () => {
        const alert0 = service.addAlert({ type: 'info', message: 'Hello Jhipster info' });
        const alert1 = service.addAlert({ type: 'info', message: 'Hello Jhipster info 2' });
        const alert2 = service.addAlert({ type: 'success', message: 'Hello Jhipster success' });
        expect(alert2).toEqual(
            expect.objectContaining({
                type: 'success',
                message: 'Hello Jhipster success',
                id: 2,
            } as Alert),
        );

        expect(service.get().length).toBe(3);
        alert1.close?.(service.get());
        expect(service.get().length).toBe(2);
        expect(service.get()[1]).not.toEqual(
            expect.objectContaining({
                type: 'info',
                message: 'Hello Jhipster info 2',
                id: 1,
            } as Alert),
        );
        alert2.close?.(service.get());
        expect(service.get().length).toBe(1);
        expect(service.get()[0]).not.toEqual(
            expect.objectContaining({
                type: 'success',
                message: 'Hello Jhipster success',
                id: 2,
            } as Alert),
        );
        alert0.close?.(service.get());
        expect(service.get().length).toBe(0);
    });

    it('should close an alert on timeout correctly', () => {
        service.addAlert({ type: 'info', message: 'Hello Jhipster info' });

        expect(service.get().length).toBe(1);

        jest.advanceTimersByTime(9000);

        expect(service.get().length).toBe(0);
    });

    it('should clear alerts', () => {
        service.addAlert({ type: 'info', message: 'Hello Jhipster info' });
        service.addAlert({ type: 'danger', message: 'Hello Jhipster info' });
        service.addAlert({ type: 'success', message: 'Hello Jhipster info' });
        expect(service.get().length).toBe(3);
        service.clear();
        expect(service.get().length).toBe(0);
    });

    it('should produce a scoped alert', () => {
        expect(service.addAlert(alertSample, [])).toEqual(expect.objectContaining(alertSampleWithId as Alert));
        expect(service.get().length).toBe(0);
    });

    it('should produce a success message', () => {
        expect(service.addAlert({ type: 'success', message: 'Hello Jhipster' })).toEqual(
            expect.objectContaining({
                type: 'success',
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a error message', () => {
        expect(service.addAlert({ type: 'danger', message: 'Hello Jhipster' })).toEqual(
            expect.objectContaining({
                type: 'danger',
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a warning message', () => {
        expect(service.addAlert({ type: 'warning', message: 'Hello Jhipster' })).toEqual(
            expect.objectContaining({
                type: 'warning',
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a info message', () => {
        expect(service.addAlert({ type: 'info', message: 'Hello Jhipster' })).toEqual(
            expect.objectContaining({
                type: 'info',
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a info message with translated message if key exists', inject([TranslateService], (translateService: TranslateService) => {
        translateService.setTranslation('en', {
            'hello.jhipster': 'Translated message',
        });
        expect(service.addAlert({ type: 'info', message: 'Hello Jhipster', translationKey: 'hello.jhipster' })).toEqual(
            expect.objectContaining({
                type: 'info',
                message: 'Translated message',
            } as Alert),
        );
    }));

    it('should produce a info message with provided message if key does not exists', () => {
        expect(service.addAlert({ type: 'info', message: 'Hello Jhipster', translationKey: 'hello.jhipster' })).toEqual(
            expect.objectContaining({
                type: 'info',
                message: 'Hello Jhipster',
            } as Alert),
        );
    });

    it('should produce a info message with provided key if translation key does not exist in translations and message is not provided', () => {
        expect(service.addAlert({ type: 'info', translationKey: 'hello.jhipster' })).toEqual(
            expect.objectContaining({
                type: 'info',
                message: 'hello.jhipster',
            } as Alert),
        );
    });
});

import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { getCurrentLocaleSignal, isErrorAlert, onError } from 'app/shared/util/global.utils';

describe('GlobalUtils', () => {
    describe('onError', () => {
        let mockAlertService: jest.Mocked<AlertService>;

        beforeEach(() => {
            mockAlertService = {
                error: jest.fn(),
                addAlert: jest.fn(),
            } as unknown as jest.Mocked<AlertService>;
        });

        it('should show error.http.400 for status 400', () => {
            const error = new HttpErrorResponse({ status: 400 });

            onError(mockAlertService, error);

            expect(mockAlertService.error).toHaveBeenCalledWith('error.http.400');
            expect(mockAlertService.addAlert).not.toHaveBeenCalled();
        });

        it('should show error.http.403 for status 403', () => {
            const error = new HttpErrorResponse({ status: 403 });

            onError(mockAlertService, error);

            expect(mockAlertService.error).toHaveBeenCalledWith('error.http.403');
            expect(mockAlertService.addAlert).not.toHaveBeenCalled();
        });

        it('should show error.http.404 for status 404', () => {
            const error = new HttpErrorResponse({ status: 404 });

            onError(mockAlertService, error);

            expect(mockAlertService.error).toHaveBeenCalledWith('error.http.404');
            expect(mockAlertService.addAlert).not.toHaveBeenCalled();
        });

        it('should show error.http.405 for status 405', () => {
            const error = new HttpErrorResponse({ status: 405 });

            onError(mockAlertService, error);

            expect(mockAlertService.error).toHaveBeenCalledWith('error.http.405');
            expect(mockAlertService.addAlert).not.toHaveBeenCalled();
        });

        it('should not show any alert for status 500', () => {
            const error = new HttpErrorResponse({ status: 500 });

            onError(mockAlertService, error);

            expect(mockAlertService.error).not.toHaveBeenCalled();
            expect(mockAlertService.addAlert).not.toHaveBeenCalled();
        });

        it('should show danger alert with error message for unknown status codes', () => {
            const error = new HttpErrorResponse({ status: 418, statusText: "I'm a teapot" });

            onError(mockAlertService, error);

            expect(mockAlertService.error).not.toHaveBeenCalled();
            expect(mockAlertService.addAlert).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: error.message,
                disableTranslation: true,
            });
        });

        it('should use disableTranslation=true by default for unknown status codes', () => {
            const error = new HttpErrorResponse({ status: 502 });

            onError(mockAlertService, error);

            expect(mockAlertService.addAlert).toHaveBeenCalledWith(
                expect.objectContaining({
                    disableTranslation: true,
                }),
            );
        });

        it('should respect disableTranslation=false parameter for unknown status codes', () => {
            const error = new HttpErrorResponse({ status: 502 });

            onError(mockAlertService, error, false);

            expect(mockAlertService.addAlert).toHaveBeenCalledWith(
                expect.objectContaining({
                    disableTranslation: false,
                }),
            );
        });
    });

    describe('isErrorAlert', () => {
        it('should return true when error has errorKey', () => {
            const error = {
                error: {
                    errorKey: 'someError',
                },
            };

            const result = isErrorAlert(error);

            expect(result).toBeTrue();
        });

        it('should return false when error does not have errorKey', () => {
            const error = {
                error: {
                    message: 'Some error message',
                },
            };

            const result = isErrorAlert(error);

            expect(result).toBeFalse();
        });

        it('should return false when error is null', () => {
            const result = isErrorAlert(null);

            expect(result).toBeFalse();
        });

        it('should return false when error.error is undefined', () => {
            const error = {};

            const result = isErrorAlert(error);

            expect(result).toBeFalse();
        });

        it('should return false when errorKey is empty string', () => {
            const error = {
                error: {
                    errorKey: '',
                },
            };

            const result = isErrorAlert(error);

            expect(result).toBeFalse();
        });

        it('should return false when errorKey is null', () => {
            const error = {
                error: {
                    errorKey: null,
                },
            };

            const result = isErrorAlert(error);

            expect(result).toBeFalse();
        });
    });

    describe('getCurrentLocaleSignal', () => {
        let translateService: TranslateService;
        let langChangeSubject: Subject<{ lang: string }>;

        beforeEach(() => {
            langChangeSubject = new Subject<{ lang: string }>();

            TestBed.configureTestingModule({
                providers: [
                    {
                        provide: TranslateService,
                        useValue: {
                            onLangChange: langChangeSubject.asObservable(),
                            getCurrentLang: jest.fn().mockReturnValue('en'),
                        },
                    },
                ],
            });

            translateService = TestBed.inject(TranslateService);
        });

        it('should create signal with initial language', () => {
            const signal = TestBed.runInInjectionContext(() => getCurrentLocaleSignal(translateService));

            expect(signal()).toBe('en');
            expect(translateService.getCurrentLang).toHaveBeenCalled();
        });

        it('should update signal when language changes', () => {
            const signal = TestBed.runInInjectionContext(() => getCurrentLocaleSignal(translateService));

            expect(signal()).toBe('en');

            langChangeSubject.next({ lang: 'de' });

            expect(signal()).toBe('de');
        });

        it('should update signal multiple times for multiple language changes', () => {
            const signal = TestBed.runInInjectionContext(() => getCurrentLocaleSignal(translateService));

            expect(signal()).toBe('en');

            langChangeSubject.next({ lang: 'de' });
            expect(signal()).toBe('de');

            langChangeSubject.next({ lang: 'fr' });
            expect(signal()).toBe('fr');

            langChangeSubject.next({ lang: 'es' });
            expect(signal()).toBe('es');
        });
    });
});

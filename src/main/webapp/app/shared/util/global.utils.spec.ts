import { HttpErrorResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';

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
});

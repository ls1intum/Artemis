import { Signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { map } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';

/**
 * Use alert service to show the error message from the error response
 * @param alertService the service used to show the exception messages to the user
 * @param error the error response that's status is used to determine the error message
 * @param disableTranslation whether the error message should be translated
 */
export const onError = (alertService: AlertService, error: HttpErrorResponse, disableTranslation: boolean = true) => {
    switch (error.status) {
        case 400:
            alertService.error('error.http.400');
            break;
        case 403:
            alertService.error('error.http.403');
            break;
        case 404:
            alertService.error('error.http.404');
            break;
        case 405:
            alertService.error('error.http.405');
            break;
        case 500:
            // Removed to avoid alerts about internal errors as the user can't do anything about it
            // and the alert does not provide any other value
            break;
        default:
            alertService.addAlert({
                type: AlertType.DANGER,
                message: error.message,
                disableTranslation: disableTranslation,
            });
            break;
    }
};

export function getCurrentLocaleSignal(translateService: TranslateService): Signal<string> {
    return toSignal(translateService.onLangChange.pipe(map((event) => event.lang)), {
        initialValue: translateService.getCurrentLang(),
    });
}

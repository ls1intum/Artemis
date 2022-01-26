import { Injectable, NgZone, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { EventManager, EventWithContent } from 'app/core/util/event-manager.service';
import { AlertError } from 'app/shared/alert/alert-error.model';
import { Subscription } from 'rxjs';
import { captureException } from '@sentry/browser';

export type AlertType = 'success' | 'danger' | 'warning' | 'info';

interface AlertBaseInternal {
    type: AlertType;
    message?: string;
    timeout?: number;
    action?: { readonly label: string; readonly callback: (alert: Alert) => void };
    onClose?: (alert: Alert) => void;
    dismissible?: boolean;
}

export interface AlertCreationProperties extends AlertBaseInternal {
    translationKey?: string;
    translationParams?: { [key: string]: unknown };
    disableTranslation?: boolean;
}

export interface Alert extends Readonly<AlertBaseInternal> {
    readonly close: () => void;
    readonly isOpen: boolean;
}

interface AlertInternal extends AlertBaseInternal {
    closeFunction?: (callback: () => void) => void;
    close: () => void;
    isOpen: boolean;
    action?: { label: string; callback: (alert: Alert) => void };
}

const DEFAULT_TIMEOUT = 8000;
const DEFAULT_DISMISSIBLE = true;

@Injectable({
    providedIn: 'root',
})
export class AlertService {
    private alerts: AlertInternal[] = [];

    errorListener: Subscription;
    httpErrorListener: Subscription;

    constructor(private sanitizer: DomSanitizer, private ngZone: NgZone, private translateService: TranslateService, private eventManager: EventManager) {
        this.errorListener = eventManager.subscribe('artemisApp.error', (response: EventWithContent<unknown> | string) => {
            const errorResponse = (response as EventWithContent<AlertError>).content;
            this.addErrorAlert(errorResponse.message, errorResponse.translationKey, errorResponse.translationParams);
        });

        this.httpErrorListener = eventManager.subscribe('artemisApp.httpError', (response: any) => {
            const httpErrorResponse = response.content;
            switch (httpErrorResponse.status) {
                // connection refused, server not reachable
                case 0:
                    this.addErrorAlert('Server not reachable', 'error.server.not.reachable');
                    break;

                case 400:
                    if (httpErrorResponse.error !== null && httpErrorResponse.error.skipAlert) {
                        break;
                    }
                    const arr = httpErrorResponse.headers.keys();
                    let errorHeader = null;
                    let entityKey = null;
                    arr.forEach((entry: string) => {
                        if (entry.toLowerCase().endsWith('app-error')) {
                            errorHeader = httpErrorResponse.headers.get(entry);
                        } else if (entry.toLowerCase().endsWith('app-params')) {
                            entityKey = httpErrorResponse.headers.get(entry);
                        }
                    });
                    if (errorHeader) {
                        const entityName = translateService.instant('global.menu.entities.' + entityKey);
                        this.addErrorAlert(errorHeader, errorHeader, { entityName });
                    } else if (httpErrorResponse.error && httpErrorResponse.error.fieldErrors) {
                        const fieldErrors = httpErrorResponse.error.fieldErrors;
                        for (const fieldError of fieldErrors) {
                            // This is most likely related to server side field validations and gentrifies the error message
                            // to only tell the user that the size is wrong instead of the specific field
                            if (['Min', 'Max', 'DecimalMin', 'DecimalMax'].includes(fieldError.message)) {
                                fieldError.message = 'Size';
                            }
                            // convert 'something[14].other[4].id' to 'something[].other[].id' so translations can be written to it
                            const convertedField = fieldError.field.replace(/\[\d*\]/g, '[]');
                            const fieldName = translateService.instant('artemisApp.' + fieldError.objectName + '.' + convertedField);
                            this.addErrorAlert('Error on field "' + fieldName + '"', 'error.' + fieldError.message, { fieldName });
                        }
                    } else if (httpErrorResponse.error && httpErrorResponse.error.message) {
                        this.addErrorAlert(httpErrorResponse.error.message, httpErrorResponse.error.message, httpErrorResponse.error.params);
                    } else {
                        this.addErrorAlert(httpErrorResponse.error);
                    }
                    break;

                case 404:
                    this.addErrorAlert('Not found', 'error.url.not.found');
                    break;

                default:
                    if (httpErrorResponse.error && httpErrorResponse.error.message) {
                        this.addErrorAlert(httpErrorResponse.error.message);
                    } else {
                        this.addErrorAlert(httpErrorResponse.error);
                    }
            }
        });
    }

    closeAll(): void {
        [...this.alerts].forEach((alert) => alert.close());
    }

    get(): Alert[] {
        return this.alerts;
    }

    /**
     * Adds alert to alerts array and returns added alert.
     * @param alert      Alert to add. If `timeout`, `toast` or `position` is missing then applying default value.
     *                   If `translateKey` is available then it's translation else `message` is used for showing.
     * @returns  Added alert
     */
    addAlert(alert: AlertCreationProperties): Alert {
        // Defensive copy to prevent overwrites
        const alertInternal = { ...alert } as AlertInternal;
        alertInternal.isOpen = true;

        if (!alert.disableTranslation && (alert.translationKey || alert.message)) {
            // in case a translation key is defined, we use it to create the message
            // Note: in most cases, our code passes the translation key as message
            let translationKey: string;
            if (alert.translationKey) {
                translationKey = alert.translationKey;
            } else {
                translationKey = alert.message!;
            }

            const translatedMessage = this.translateService.instant(translationKey, alert.translationParams);

            const translationFound = !translatedMessage.startsWith(translationNotFoundMessage);
            if (translationFound) {
                alertInternal.message = translatedMessage;
            } else {
                // Sent a sentry warning with the unknown translation key
                captureException(new Error('Unknown translation key: ' + translationKey));

                // Fallback to displaying the translation key
                // Keeping the original message if it exists in case the message field is supplied with a default english version, and
                // the translationKey field is used for translations
                if (!alert.message) {
                    alertInternal.message = translationKey;
                }
            }
        }

        alertInternal.message = this.sanitizer.sanitize(SecurityContext.HTML, alertInternal.message ?? '') ?? '';
        alertInternal.timeout = alertInternal.timeout ?? DEFAULT_TIMEOUT;
        alertInternal.dismissible = alertInternal.dismissible ?? DEFAULT_DISMISSIBLE;
        alertInternal.close = () => {
            alertInternal.isOpen = false;
            const alertIndex = this.alerts.indexOf(alertInternal);
            if (alertIndex >= 0) {
                this.alerts.splice(alertIndex, 1);
                if (alertInternal.onClose) {
                    alertInternal.onClose(alertInternal);
                }
            }
        };
        if (alertInternal.action) {
            alertInternal.action = {
                label: this.sanitizer.sanitize(SecurityContext.HTML, this.translateService.instant(alertInternal.action.label) ?? '') ?? '',
                callback: alertInternal.action.callback,
            };
        }

        this.alerts.unshift(alertInternal);

        if (alertInternal.timeout > 0) {
            // Workaround protractor waiting for setTimeout.
            // Reference https://www.protractortest.org/#/timeouts
            this.ngZone.runOutsideAngular(() => {
                setTimeout(() => {
                    this.ngZone.run(() => {
                        alertInternal.close();
                    });
                }, alertInternal.timeout);
            });
        }

        return alertInternal;
    }

    success(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: 'success',
            message,
            translationParams,
        });
    }

    error(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: 'danger',
            message,
            translationParams,
        });
    }

    warning(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: 'warning',
            message,
            translationParams,
        });
    }

    info(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: 'info',
            message,
            translationParams,
        });
    }

    private addErrorAlert(message?: string, translationKey?: string, translationParams?: { [key: string]: unknown }): void {
        this.addAlert({ type: 'danger', message, translationKey, translationParams });
    }
}

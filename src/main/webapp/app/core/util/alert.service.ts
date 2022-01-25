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

export interface AlertCreationProperties extends Readonly<AlertBaseInternal> {
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

@Injectable({
    providedIn: 'root',
})
export class AlertService {
    timeout = 8000;
    dismissible = true;

    // unique id for each alert. Starts from 0.
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
                            // This is most likely related to server side field validations and gentrifies the error message to only tell the user that the size is wrong
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
        const alertInternal = alert as AlertInternal;
        alertInternal.isOpen = true;

        if (!alert.disableTranslation) {
            if (alert.translationKey) {
                // in case a translation key is defined, we use it to create the message
                const translatedMessage = this.translateService.instant(alert.translationKey, alert.translationParams);
                // if translation key exists
                if (translatedMessage !== `${translationNotFoundMessage}[${alert.translationKey}]`) {
                    alertInternal.message = translatedMessage;
                } else if (!alertInternal.message) {
                    alertInternal.message = alert.translationKey;
                }
            } else if (alertInternal.message) {
                // Note: in most cases, our code passes the translation key as message
                alertInternal.message = this.translateService.instant(alertInternal.message, alert.translationParams);
            }

            if (alertInternal.message?.startsWith('translation-not-found')) {
                // In case a translation key is not found, remove the 'translation-not-found[...]' annotation
                const alertMessageMatch = alertInternal.message.match(/translation-not-found\[(.*?)\]$/);
                // Sent a sentry warning with the translation key
                captureException(new Error('Unknown translation key: ' + alert.message));
                if (alertMessageMatch && alertMessageMatch.length > 1) {
                    alertInternal.message = alertMessageMatch[1];
                } else {
                    // Fallback, in case the bracket is missing
                    alertInternal.message = alertInternal.message.replace('translation-not-found', '');
                }
            }
        }

        alertInternal.message = this.sanitizer.sanitize(SecurityContext.HTML, alertInternal.message ?? '') ?? '';
        alertInternal.timeout = alertInternal.timeout ?? this.timeout;
        alertInternal.dismissible = alertInternal.dismissible ?? this.dismissible;
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
            alertInternal.action.label = this.sanitizer.sanitize(SecurityContext.HTML, this.translateService.instant(alertInternal.action.label) ?? '') ?? '';
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
            timeout: this.timeout,
        });
    }

    error(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: 'danger',
            message,
            translationParams,
            timeout: this.timeout,
        });
    }

    warning(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: 'warning',
            message,
            translationParams,
            timeout: this.timeout,
        });
    }

    info(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: 'info',
            message,
            translationParams,
            timeout: this.timeout,
        });
    }

    private addErrorAlert(message?: string, translationKey?: string, translationParams?: { [key: string]: unknown }): void {
        this.addAlert({ type: 'danger', message, translationKey, translationParams });
    }
}

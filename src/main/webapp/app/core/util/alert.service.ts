import { Injectable, NgZone, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { EventManager, EventWithContent } from 'app/core/util/event-manager.service';
import { AlertError } from 'app/shared/alert/alert-error.model';
import { Subscription } from 'rxjs';
import { captureException } from '@sentry/browser';
import { faCheckCircle, faExclamationCircle, faExclamationTriangle, faInfoCircle, IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';

export class AlertType {
    public static readonly SUCCESS = new AlertType(faCheckCircle, 'success', 'btn-success');
    public static readonly DANGER = new AlertType(faExclamationCircle, 'danger', 'btn-danger');
    public static readonly WARNING = new AlertType(faExclamationTriangle, 'warning', 'btn-warning');
    public static readonly INFO = new AlertType(faInfoCircle, 'info', 'btn-info');

    private constructor(icon: IconDefinition, containerClassName: string, buttonClassName: string) {
        this.icon = icon;
        this.containerClassName = containerClassName;
        this.buttonClassName = buttonClassName;
    }

    public readonly icon: IconDefinition;
    public readonly containerClassName: string;
    public readonly buttonClassName: string;
}

interface AlertBase {
    type: AlertType;
    message?: string;
    timeout?: number;
    action?: { readonly label: string; readonly callback: (alert: Alert) => void };
    onClose?: (alert: Alert) => void;
    dismissible?: boolean;
}

export interface AlertCreationProperties extends AlertBase {
    translationKey?: string;
    translationParams?: { [key: string]: unknown };
    disableTranslation?: boolean;
}

interface AlertInternal extends AlertBase {
    close: () => void;
    isOpen: boolean;
    openedAt?: dayjs.Dayjs;
}

export interface Alert extends Readonly<AlertInternal> {}

const DEFAULT_TIMEOUT = 15000;
const DEFAULT_DISMISSIBLE = true;

@Injectable({
    providedIn: 'root',
})
export class AlertService {
    private alerts: AlertInternal[] = [];

    errorListener: Subscription;
    httpErrorListener: Subscription;

    readonly conflictErrorKeysToSkip: string[] = ['cannotRegisterInstructor'];

    constructor(private sanitizer: DomSanitizer, private ngZone: NgZone, private translateService: TranslateService, private eventManager: EventManager) {
        this.errorListener = eventManager.subscribe('artemisApp.error', (response: EventWithContent<unknown> | string) => {
            const errorResponse = (response as EventWithContent<AlertError>).content;
            this.addErrorAlert(errorResponse.message, errorResponse.translationKey, errorResponse.translationParams);
        });

        this.httpErrorListener = eventManager.subscribe('artemisApp.httpError', (response: any) => {
            const httpErrorResponse: HttpErrorResponse = response.content;
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
                    if (errorHeader && !translateService.instant(errorHeader).startsWith(translationNotFoundMessage)) {
                        const entityName = translateService.instant('global.menu.entities.' + entityKey);
                        this.addErrorAlert(errorHeader, errorHeader, { entityName, ...httpErrorResponse.error?.params });
                    } else if (httpErrorResponse.error && httpErrorResponse.error.fieldErrors) {
                        const fieldErrors = httpErrorResponse.error.fieldErrors;
                        for (const fieldError of fieldErrors) {
                            // This is most likely related to server side field validations and gentrifies the error message
                            // to only tell the user that the size is wrong instead of the specific field
                            if (['Min', 'Max', 'DecimalMin', 'DecimalMax'].includes(fieldError.message)) {
                                fieldError.message = 'size';
                            }
                            // convert 'something[14].other[4].id' to 'something[].other[].id' so translations can be written to it
                            const convertedField = fieldError.field.replace(/\[\d*\]/g, '[]');
                            const fieldName = translateService.instant('artemisApp.' + fieldError.objectName + '.' + convertedField);
                            this.addErrorAlert('Error on field "' + fieldName + '"', 'error.' + fieldError.message, { fieldName });
                        }
                    } else if (httpErrorResponse.error && httpErrorResponse.error.title) {
                        this.addErrorAlert(httpErrorResponse.error.title, httpErrorResponse.error.message, httpErrorResponse.error.params);
                    }
                    break;

                case 404:
                    // Disabled
                    break;

                default:
                    if (httpErrorResponse.error && httpErrorResponse.error.title) {
                        // To avoid displaying this alerts twice, we need to filter the received errors. In this case, we filter for the cannot register instructor error.
                        if (httpErrorResponse.status === 403 && this.conflictErrorKeysToSkip.includes(httpErrorResponse.error.errorKey)) {
                            break;
                        }
                        this.addErrorAlert(httpErrorResponse.error.title, httpErrorResponse.error.message, httpErrorResponse.error.params);
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
     * @param alert   Alert specification to add. If `timeout` or `dismissible` is missing then applying default value.
     *                Set timeout to zero to disable timeout
     * @returns       Added alert
     */
    addAlert(alert: AlertCreationProperties): Alert {
        // Defensive copy to prevent overwrites
        const alertInternal: AlertInternal = {
            type: alert.type,
            message: alert.message,
            timeout: alert.timeout,
            action: alert.action,
            onClose: alert.onClose,
            dismissible: alert.dismissible,
            isOpen: false,
        } as AlertInternal;

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

        if (alert.message) {
            alertInternal.isOpen = true;
            alertInternal.openedAt = dayjs();

            // Due to duplicate alerts spawned by the global http error interceptor and some components,
            // we prevent more than one alert with the same content to be spawned within 50 milliseconds.
            // If such an alert already exists, we return the old one instead.
            const olderAlertWithIdenticalContent: AlertInternal | undefined = this.alerts.find(
                (otherAlert) => alertInternal.message === otherAlert.message && Math.abs(alertInternal.openedAt!.diff(otherAlert.openedAt!, 'ms')) <= 50,
            );
            if (olderAlertWithIdenticalContent) {
                return olderAlertWithIdenticalContent;
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
        }

        return alertInternal;
    }

    success(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: AlertType.SUCCESS,
            message,
            translationParams,
        });
    }

    error(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: AlertType.DANGER,
            message,
            translationParams,
        });
    }

    warning(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: AlertType.WARNING,
            message,
            translationParams,
        });
    }

    info(message: string, translationParams?: { [key: string]: unknown }): Alert {
        return this.addAlert({
            type: AlertType.INFO,
            message,
            translationParams,
        });
    }

    addErrorAlert(message?: any, translationKey?: string, translationParams?: { [key: string]: unknown }): void {
        if (message && typeof message !== 'string') {
            message = '' + message;
        }
        this.addAlert({ type: AlertType.DANGER, message, translationKey, translationParams });
    }
}

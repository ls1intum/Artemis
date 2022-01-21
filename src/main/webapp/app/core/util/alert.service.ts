import { Injectable, NgZone, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { translationNotFoundMessage } from 'app/core/config/translation.config';
import { EventManager, EventWithContent } from 'app/core/util/event-manager.service';
import { AlertError } from 'app/shared/alert/alert-error.model';
import { Subscription } from 'rxjs';

export type AlertType = 'success' | 'danger' | 'warning' | 'info';

export interface Alert {
    id?: number;
    type: AlertType;
    message?: string;
    translationKey?: string;
    translationParams?: { [key: string]: unknown };
    timeout?: number;
    close?: () => void;
    action?: { label: string; callback: () => void };
    dismissible?: boolean;
}

@Injectable({
    providedIn: 'root',
})
export class AlertService {
    timeout = 8000;
    dismissible = true;

    // unique id for each alert. Starts from 0.
    private alertId = 0;
    private alerts: Alert[] = [];

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

    clear(): void {
        this.alerts = [];
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
    addAlert(alert: Alert): Alert {
        alert.id = this.alertId++;

        if (alert.translationKey) {
            // in case a translation key is defined, we use it to create the message
            const translatedMessage = this.translateService.instant(alert.translationKey, alert.translationParams);
            // if translation key exists
            if (translatedMessage !== `${translationNotFoundMessage}[${alert.translationKey}]`) {
                alert.message = translatedMessage;
            } else if (!alert.message) {
                alert.message = alert.translationKey;
            }
        } else if (alert.message) {
            // Note: in most cases, our code passes the translation key as message
            alert.message = this.translateService.instant(alert.message, alert.translationParams);
        }

        alert.message = this.sanitizer.sanitize(SecurityContext.HTML, alert.message ?? '') ?? '';
        alert.timeout = alert.timeout ?? this.timeout;
        alert.dismissible = alert.dismissible ?? (alert.action ? false : this.dismissible);
        alert.close = () => {
            const alertIndex = this.alerts.indexOf(alert);
            if (alertIndex >= 0) {
                this.alerts.splice(alertIndex, 1);
            }
        };
        if (alert.action) {
            alert.action.label = this.sanitizer.sanitize(SecurityContext.HTML, this.translateService.instant(alert.action.label) ?? '') ?? '';
        }

        this.alerts.splice(0, 0, alert);

        if (alert.timeout > 0) {
            // Workaround protractor waiting for setTimeout.
            // Reference https://www.protractortest.org/#/timeouts
            this.ngZone.runOutsideAngular(() => {
                setTimeout(() => {
                    this.ngZone.run(() => {
                        alert.close!();
                    });
                }, alert.timeout);
            });
        }

        return alert;
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

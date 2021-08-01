import { Component, OnDestroy } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { Alert, AlertService } from 'app/core/util/alert.service';
import { AlertError } from 'app/shared/alert/alert-error.model';
import { checkForMissingTranslationKey } from 'app/shared/util/utils';
import { EventManager, EventWithContent } from 'app/core/util/event-manager.service';

@Component({
    selector: 'jhi-alert-error',
    templateUrl: './alert-error.component.html',
})
export class AlertErrorComponent implements OnDestroy {
    alerts: Alert[] = [];
    errorListener: Subscription;
    httpErrorListener: Subscription;

    constructor(private alertService: AlertService, private eventManager: EventManager, private translateService: TranslateService) {
        this.errorListener = eventManager.subscribe('artemisApp.error', (response: EventWithContent<unknown> | string) => {
            const errorResponse = (response as EventWithContent<AlertError>).content;
            this.addErrorAlert(errorResponse.message, errorResponse.key, errorResponse.params);
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

    /**
     * set classes for alert
     * @param {Alert} alert
     * @return {{ [key: string]: boolean }}
     */
    setClasses(alert: Alert): { [key: string]: boolean } {
        const classes = { 'jhi-toast': Boolean(alert.toast) };
        if (alert.position) {
            return { ...classes, [alert.position]: true };
        }
        return classes;
    }

    /**
     * remove listeners on destroy
     */
    ngOnDestroy(): void {
        if (this.errorListener) {
            this.eventManager.destroy(this.errorListener);
        }
        if (this.httpErrorListener) {
            this.eventManager.destroy(this.httpErrorListener);
        }
    }

    close(alert: Alert): void {
        alert.close?.(this.alerts);
    }

    /**
     * add a new alert
     * @param {string} message
     * @param {string} translationKey?
     * @param {any} translationParams?
     */
    private addErrorAlert(message?: string, translationKey?: string, translationParams?: { [key: string]: unknown }): void {
        this.alertService.addAlert({ type: 'danger', message, translationKey, translationParams }, this.alerts);
    }

    /**
     * The received alert may contain a message which could not be translated.
     * We slice the wrapping 'translation-not-found[..]' and return the response.
     * @param alert which contains the alert message
     */
    getAlertMessage(alert: Alert) {
        checkForMissingTranslationKey(alert);
        return alert.message;
    }
}

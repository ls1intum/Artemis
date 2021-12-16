import { Injectable, SecurityContext, NgZone } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { translationNotFoundMessage } from 'app/core/config/translation.config';

export type AlertType = 'success' | 'danger' | 'warning' | 'info';

export interface Alert {
    id?: number;
    type: AlertType;
    message?: string;
    translationKey?: string;
    translationParams?: { [key: string]: unknown };
    timeout?: number;
    toast?: boolean;
    position?: string;
    close?: () => void;
    action?: { label: string; callback: () => void };
    dismissible?: boolean;
}

@Injectable({
    providedIn: 'root',
})
export class AlertService {
    timeout = 8000;
    toast = false;
    dismissible = true;
    position = 'top right';

    // unique id for each alert. Starts from 0.
    private alertId = 0;
    private alerts: Alert[] = [];

    constructor(private sanitizer: DomSanitizer, private ngZone: NgZone, private translateService: TranslateService) {}

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
     * @param extAlerts  If missing then adding `alert` to `AlertService` internal array and alerts can be retrieved by `get()`.
     *                   Else adding `alert` to `extAlerts`.
     * @returns  Added alert
     */
    addAlert(alert: Alert, extAlerts?: Alert[]): Alert {
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
        alert.toast = alert.toast ?? this.toast;
        alert.position = alert.position ?? this.position;
        alert.dismissible = alert.dismissible ?? (alert.action ? false : this.dismissible);
        alert.close = () => this.closeAlert(alert.id!, extAlerts ?? this.alerts);

        if (alert.action) {
            alert.action.label = this.sanitizer.sanitize(SecurityContext.HTML, this.translateService.instant(alert.action.label) ?? '') ?? '';
        }

        (extAlerts ?? this.alerts).push(alert);

        if (alert.timeout > 0) {
            // Workaround protractor waiting for setTimeout.
            // Reference https://www.protractortest.org/#/timeouts
            this.ngZone.runOutsideAngular(() => {
                setTimeout(() => {
                    this.ngZone.run(() => {
                        this.closeAlert(alert.id!, extAlerts ?? this.alerts);
                    });
                }, alert.timeout);
            });
        }

        return alert;
    }

    success(message: string, translationParams?: { [key: string]: unknown }, position?: string): Alert {
        return this.addAlert({
            type: 'success',
            message,
            translationParams,
            timeout: this.timeout,
            toast: this.isToast(),
            position,
        });
    }

    error(message: string, translationParams?: { [key: string]: unknown }, position?: string): Alert {
        return this.addAlert({
            type: 'danger',
            message,
            translationParams,
            timeout: this.timeout,
            toast: this.isToast(),
            position,
        });
    }

    warning(message: string, translationParams?: { [key: string]: unknown }, position?: string): Alert {
        return this.addAlert({
            type: 'warning',
            message,
            translationParams,
            timeout: this.timeout,
            toast: this.isToast(),
            position,
        });
    }

    info(message: string, translationParams?: { [key: string]: unknown }, position?: string): Alert {
        return this.addAlert({
            type: 'info',
            message,
            translationParams,
            timeout: this.timeout,
            toast: this.isToast(),
            position,
        });
    }

    isToast(): boolean {
        return this.toast;
    }

    private closeAlert(alertId: number, extAlerts?: Alert[]): void {
        const alerts = extAlerts ?? this.alerts;
        const alertIndex = alerts.map((alert) => alert.id).indexOf(alertId);
        // if found alert then remove
        if (alertIndex >= 0) {
            alerts.splice(alertIndex, 1);
        }
    }
}

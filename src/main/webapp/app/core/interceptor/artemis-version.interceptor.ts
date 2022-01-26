import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { tap, throttleTime } from 'rxjs/operators';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

@Injectable()
export class ArtemisVersionInterceptor implements HttpInterceptor {
    private showAlert = new Subject<void>();

    constructor(alertService: AlertService, private serverDateService: ArtemisServerDateService) {
        this.showAlert.pipe(throttleTime(10000)).subscribe(() => {
            // show the outdated alert for 30s so users update by reloading the browser, only show this every 10s
            alertService.addAlert({ type: AlertType.INFO, message: 'artemisApp.outdatedAlert', timeout: 30000 });
        });
    }

    intercept(request: HttpRequest<any>, nextHandler: HttpHandler): Observable<HttpEvent<any>> {
        return nextHandler.handle(request).pipe(
            tap((response) => {
                if (response instanceof HttpResponse) {
                    const isTranslationStringsRequest = response.url?.includes('/i18n/');
                    const serverVersion = response.headers.get(ARTEMIS_VERSION_HEADER);
                    if (VERSION && serverVersion && VERSION !== serverVersion && !isTranslationStringsRequest) {
                        this.showAlert.next();
                    }
                    // only invoke the time call if the call was not already the time call to prevent recursion here
                    if (!request.url.includes('time')) {
                        this.serverDateService.updateTime();
                    }
                }
            }),
        );
    }
}

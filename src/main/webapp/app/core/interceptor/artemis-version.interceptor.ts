import { Injectable, Injector } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { tap, throttleTime } from 'rxjs/operators';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { CheckForUpdateService } from 'app/core/update/check-for-update.service';

@Injectable()
export class ArtemisVersionInterceptor implements HttpInterceptor {
    private triggerUpdateService = new Subject<void>();

    constructor(injector: Injector, private serverDateService: ArtemisServerDateService) {
        // only trigger update service every 10s
        this.triggerUpdateService.pipe(throttleTime(10000)).subscribe(() => {
            // Workaround: Random cyclic dependency on token HTTP_INTERCEPTOR
            const checkForUpdateService = injector.get(CheckForUpdateService);
            checkForUpdateService.checkForUpdates();
        });
    }

    intercept(request: HttpRequest<any>, nextHandler: HttpHandler): Observable<HttpEvent<any>> {
        return nextHandler.handle(request).pipe(
            tap((response) => {
                if (response instanceof HttpResponse) {
                    const isTranslationStringsRequest = response.url?.includes('/i18n/');
                    const serverVersion = response.headers.get(ARTEMIS_VERSION_HEADER);
                    if (VERSION && serverVersion && VERSION !== serverVersion && !isTranslationStringsRequest) {
                        this.triggerUpdateService.next();
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

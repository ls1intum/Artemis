import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { tap, throttleTime } from 'rxjs/operators';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { AlertService } from 'app/core/alert/alert.service';

@Injectable()
export class ArtemisVersionInterceptor implements HttpInterceptor {
    private showAlert = new Subject();

    constructor(alertService: AlertService) {
        this.showAlert.pipe(throttleTime(10000)).subscribe(() => alertService.addAlert({ type: 'info', msg: 'artemisApp.outdatedAlert', timeout: 30000 }, []));
    }

    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(req).pipe(
            tap((response) => {
                if (response instanceof HttpResponse) {
                    const serverVersion = response.headers.get(ARTEMIS_VERSION_HEADER);
                    if (VERSION && serverVersion && VERSION !== serverVersion) {
                        this.showAlert.next();
                    }
                }
            }),
        );
    }
}

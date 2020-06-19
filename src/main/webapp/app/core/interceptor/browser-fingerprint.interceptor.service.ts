import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BrowserFingerprintService } from 'app/shared/fingerprint/browser-fingerprint.service';

@Injectable()
export class BrowserFingerprintInterceptor implements HttpInterceptor {
    private fingerprint: string;
    private instanceIdentifier: string;

    constructor(private browserFingerprintService: BrowserFingerprintService) {
        browserFingerprintService.fingerprint.subscribe((fingerprint) => (this.fingerprint = fingerprint || ''));
        browserFingerprintService.instanceIdentifier.subscribe((instanceIdentifier) => (this.instanceIdentifier = instanceIdentifier || ''));
    }

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        request = request.clone({
            setHeaders: {
                'X-Artemis-Client-Instance-ID': this.instanceIdentifier,
                'X-Artemis-Client-Fingerprint': this.fingerprint,
            },
        });
        return next.handle(request);
    }
}

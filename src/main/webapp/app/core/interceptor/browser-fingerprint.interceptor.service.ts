import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BrowserFingerprintService } from 'app/shared/fingerprint/browser-fingerprint.service';
import { isRequestToArtemisServer } from './interceptor.util';

@Injectable()
export class BrowserFingerprintInterceptor implements HttpInterceptor {
    private fingerprint?: string;
    private instanceIdentifier?: string;

    constructor(private browserFingerprintService: BrowserFingerprintService) {
        browserFingerprintService.fingerprint.subscribe((fingerprint) => (this.fingerprint = fingerprint));
        browserFingerprintService.instanceIdentifier.subscribe((instanceIdentifier) => (this.instanceIdentifier = instanceIdentifier));
    }

    /**
     * Intercepts all HTTP Requests to the Artemis Server and adds Fingerprint + Instance ID as HTTP Headers
     *
     * @param request The outgoing request object to handle.
     * @param next The next interceptor in the chain, or the server if no interceptors remain in the chain.
     * @returns An observable of the event stream.
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (isRequestToArtemisServer(request) && (this.instanceIdentifier || this.fingerprint)) {
            request = request.clone({
                setHeaders: {
                    'X-Artemis-Client-Instance-ID': this.instanceIdentifier ?? '',
                    'X-Artemis-Client-Fingerprint': this.fingerprint ?? '',
                },
            });
        }

        return next.handle(request);
    }
}

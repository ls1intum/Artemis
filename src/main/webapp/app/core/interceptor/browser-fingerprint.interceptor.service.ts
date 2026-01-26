import { Injectable, inject } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { isRequestToArtemisServer } from './interceptor.util';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

/**
 * HTTP interceptor that adds browser fingerprint and instance ID headers to requests.
 * These headers are used by the server for security purposes like detecting
 * suspicious login patterns or session anomalies.
 */
@Injectable()
export class BrowserFingerprintInterceptor implements HttpInterceptor {
    private browserFingerprintService = inject(BrowserFingerprintService);

    /** Cached browser fingerprint hash */
    private cachedFingerprint?: string;
    /** Cached browser instance identifier */
    private cachedInstanceId?: string;

    constructor() {
        // Subscribe to fingerprint and instance ID updates
        this.browserFingerprintService.browserFingerprint.subscribe((fingerprint) => (this.cachedFingerprint = fingerprint));
        this.browserFingerprintService.browserInstanceId.subscribe((instanceId) => (this.cachedInstanceId = instanceId));
    }

    /**
     * Intercepts HTTP requests to the Artemis server and adds fingerprint headers.
     * Headers are only added if at least one identifier is available.
     *
     * @param request - The outgoing HTTP request
     * @param next - The next handler in the interceptor chain
     * @returns Observable of the HTTP event stream
     */
    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (isRequestToArtemisServer(request) && (this.cachedInstanceId || this.cachedFingerprint)) {
            request = request.clone({
                setHeaders: {
                    'X-Artemis-Client-Instance-ID': this.cachedInstanceId ?? '',
                    'X-Artemis-Client-Fingerprint': this.cachedFingerprint ?? '',
                },
            });
        }

        return next.handle(request);
    }
}

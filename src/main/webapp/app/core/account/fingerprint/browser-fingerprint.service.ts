import { Injectable, inject } from '@angular/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { BehaviorSubject } from 'rxjs';
import FingerprintJS, { GetResult } from '@fingerprintjs/fingerprintjs';

@Injectable({ providedIn: 'root' })
export class BrowserFingerprintService {
    private localStorageService = inject(LocalStorageService);

    private readonly BROWSER_INSTANCE_KEY = 'instanceIdentifier';

    public fingerprint = new BehaviorSubject<string | undefined>(undefined);
    public instanceIdentifier = new BehaviorSubject<string | undefined>(undefined);

    public initialize(browserFingerprintsEnabled: boolean | undefined) {
        // If undefined, still enable it to not break older configurations without the field in profile info
        if (browserFingerprintsEnabled !== false) {
            this.setFingerprint();
            this.setInstance();
        } else {
            this.clearInstance();
        }
    }

    private setFingerprint(): void {
        FingerprintJS.load().then((fingerprint: { get: () => Promise<GetResult> }) => {
            fingerprint.get().then((result) => {
                const visitorId = result.visitorId;
                this.fingerprint.next(visitorId);
            });
        });
    }

    private setInstance(): void {
        let instanceIdentifier: string | undefined = this.localStorageService.retrieve<string>(this.BROWSER_INSTANCE_KEY);
        if (!instanceIdentifier) {
            instanceIdentifier = window.crypto.randomUUID().toString();
            this.localStorageService.store<string>(this.BROWSER_INSTANCE_KEY, instanceIdentifier);
        }
        this.instanceIdentifier.next(instanceIdentifier);
    }

    private clearInstance(): void {
        this.localStorageService.remove(this.BROWSER_INSTANCE_KEY);
    }
}

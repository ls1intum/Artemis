import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { LocalStorageService } from 'ngx-webstorage';
import FingerprintJS, { GetResult } from '@fingerprintjs/fingerprintjs';
import { v4 as uuid } from 'uuid';

@Injectable({ providedIn: 'root' })
export class BrowserFingerprintService {
    private readonly BROWSER_INSTANCE_KEY = 'instanceIdentifier';

    public fingerprint = new BehaviorSubject<string | undefined>(undefined);
    public instanceIdentifier = new BehaviorSubject<string | undefined>(undefined);

    constructor(private localStorage: LocalStorageService) {}

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
        let instanceIdentifier: string | undefined = this.localStorage.retrieve(this.BROWSER_INSTANCE_KEY);
        if (!instanceIdentifier) {
            instanceIdentifier = uuid();
            this.localStorage.store(this.BROWSER_INSTANCE_KEY, instanceIdentifier);
        }
        this.instanceIdentifier.next(instanceIdentifier);
    }

    private clearInstance(): void {
        this.localStorage.clear(this.BROWSER_INSTANCE_KEY);
    }
}

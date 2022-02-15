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

    constructor(private localStorage: LocalStorageService) {
        this.setFingerprint();
        this.setInstance();
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
}

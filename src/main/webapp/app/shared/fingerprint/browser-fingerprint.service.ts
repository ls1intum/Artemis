import { Injectable } from '@angular/core';
import { Fingerprint2 as IFingerprint2 } from 'app/shared/fingerprint/fingerprint2';
import { BehaviorSubject } from 'rxjs';
import { LocalStorageService } from 'ngx-webstorage';
const Fingerprint2: IFingerprint2 = require('fingerprintjs2');
import { v4 as uuid } from 'uuid';

@Injectable({ providedIn: 'root' })
export class BrowserFingerprintService {
    private readonly BROWSER_INSTANCE_KEY = 'instanceIdentifier';

    public fingerprint = new BehaviorSubject<string | null>(null);
    public instanceIdentifier = new BehaviorSubject<string | null>(null);

    constructor(private localStorage: LocalStorageService) {
        this.setFingerprint();
        this.setInstance();
    }

    private setFingerprint(): void {
        Fingerprint2.get((components) => {
            const key = components.map((component: any) => component.value).join('');
            const seed = 31;
            const murmur = Fingerprint2.x64hash128(key, seed);
            this.fingerprint.next(murmur);
        });
    }

    private setInstance(): void {
        let instanceIdentifier: string | null = this.localStorage.retrieve(this.BROWSER_INSTANCE_KEY);
        if (!instanceIdentifier) {
            instanceIdentifier = uuid();
            this.localStorage.store(this.BROWSER_INSTANCE_KEY, instanceIdentifier);
        }
        this.instanceIdentifier.next(instanceIdentifier);
    }
}

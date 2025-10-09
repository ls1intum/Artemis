import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CloudSettingsService {
    private _cloudEnabled$ = new BehaviorSubject<boolean>(false);
    readonly enabled$ = this._cloudEnabled$.asObservable();

    setEnabled(v: boolean): void {
        this._cloudEnabled$.next(v);
    }
}

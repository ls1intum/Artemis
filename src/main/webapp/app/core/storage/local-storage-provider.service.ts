import { Injectable } from '@angular/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';

@Injectable({ providedIn: 'root' })
export class LocalStorageProvider {
    constructor(private localStorage: LocalStorageService, private sessionStorage: SessionStorageService, private jsb: JavaBridgeService) {}
}

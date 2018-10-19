import { Injectable } from '@angular/core';

import { Principal } from '../auth/principal.service';
import { AuthServerProvider, Credentials } from '../auth/auth-jwt.service';
import { JhiWebsocketService } from '../websocket/websocket.service';

@Injectable({ providedIn: 'root' })
export class LoginService {
    constructor(private principal: Principal, private trackerService: JhiTrackerService, private authServerProvider: AuthServerProvider) {}

    login(credentials: Credentials, callback?: any) {
        const cb = callback || function() {};

        return new Promise((resolve, reject) => {
            this.authServerProvider.login(credentials).subscribe(
                data => {
                    this.principal.identity(true).then(account => {
                        this.websocketService.sendActivity();
                        resolve(data);
                    });
                    return cb();
                },
                err => {
                    this.logout();
                    reject(err);
                    return cb(err);
                }
            );
        });
    }

    loginWithToken(jwt: string, rememberMe: string) {
        return this.authServerProvider.loginWithToken(jwt, rememberMe);
    }

    logout() {
        this.authServerProvider.logout().subscribe();
        this.principal.authenticate(null);
    }
}

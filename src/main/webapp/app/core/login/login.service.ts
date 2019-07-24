import { Injectable } from '@angular/core';

import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { LocalStorageService } from 'ngx-webstorage';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { CUSTOM_STUDENT_LOGIN_KEY } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class LoginService {
    constructor(
        private accountService: AccountService,
        private localStorageService: LocalStorageService,
        private websocketService: JhiWebsocketService,
        private authServerProvider: AuthServerProvider,
    ) {}

    login(credentials: Credentials, callback?: any) {
        const cb = callback || function() {};

        return new Promise((resolve, reject) => {
            this.authServerProvider.login(credentials).subscribe(
                data => {
                    this.accountService.identity(true).then(user => {
                        this.websocketService.sendActivity();
                        resolve(data);
                    });
                    return cb();
                },
                err => {
                    this.logout();
                    reject(err);
                    return cb(err);
                },
            );
        });
    }

    loginWithToken(jwt: string, rememberMe: string) {
        return this.authServerProvider.loginWithToken(jwt, rememberMe);
    }

    logout() {
        this.localStorageService.clear(CUSTOM_STUDENT_LOGIN_KEY);
        this.authServerProvider.logout().subscribe();
        this.accountService.authenticate(null);
    }
}

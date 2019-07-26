import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, from, throwError, Observable } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';

import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({ providedIn: 'root' })
export class LoginService {
    constructor(private accountService: AccountService, private websocketService: JhiWebsocketService, private authServerProvider: AuthServerProvider, private router: Router) {}

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
        this.authServerProvider
            .logout()
            .pipe(
                tap(() => {
                    this.accountService.authenticate(null);
                }),
                switchMap(() => {
                    return from(this.router.navigate([''])).pipe(switchMap((res: boolean) => (res ? EMPTY : throwError('Redirect to home failed!'))));
                }),
            )
            .subscribe();
    }
}

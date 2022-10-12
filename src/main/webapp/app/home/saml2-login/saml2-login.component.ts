import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { LoginService } from 'app/core/login/login.service';
import { Saml2Config } from 'app/home/saml2-login/saml2.config';
import { EventManager } from 'app/core/util/event-manager.service';
import { AlertService } from 'app/core/util/alert.service';
import { Route, Router } from '@angular/router';

@Component({
    selector: 'jhi-saml2-login',
    templateUrl: './saml2-login.component.html',
    styles: [],
})
export class Saml2LoginComponent implements OnInit {
    @Input()
    rememberMe = true;
    @Input()
    acceptedTerms = false;
    @Input()
    saml2Profile: Saml2Config;

    constructor(private loginService: LoginService, private eventManager: EventManager, private alertService: AlertService, private router: Router) {}

    ngOnInit(): void {
        // If SAML2 flow was started, retry login.
        if (document.cookie.indexOf('SAML2flow=') >= 0) {
            // remove cookie
            document.cookie = 'SAML2flow=; expires=Thu, 01 Jan 1970 00:00:00 UTC; ; SameSite=Lax;';
            this.loginSAML2();
        }

        this.printpath('', this.router.config);
    }

    private printpath(parent: String, config: Route[]) {
        for (const element of config) {
            const route = element;
            console.log(parent + '/' + route.path);
            if (route.children) {
                const currentPath = route.path ? parent + '/' + route.path : parent;
                this.printpath(currentPath, route.children);
            }
        }
    }

    loginSAML2() {
        this.loginService
            .loginSAML2(this.rememberMe)
            .then(() => {
                this.eventManager.broadcast({
                    name: 'authenticationSuccess',
                    content: 'Sending Authentication Success',
                });
            })
            .catch((error: HttpErrorResponse) => {
                if (error.status === 401) {
                    // (re)set cookie
                    document.cookie = 'SAML2flow=true; max-age=120; SameSite=Lax;';
                    // arbitrary by SAML2 HTTP Filter Chain secured URL
                    window.location.replace('/saml2/authenticate');
                } else if (error.status === 403) {
                    // for example if user was disabled
                    let message = 'Forbidden';
                    const details = error.headers.get('X-artemisApp-error');
                    if (details) {
                        message += ': ' + details;
                    }
                    this.alertService.warning(message);
                }
            });
    }
}

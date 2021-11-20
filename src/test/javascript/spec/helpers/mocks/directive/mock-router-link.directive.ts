import { Directive, HostListener, Input } from '@angular/core';
import { Router } from '@angular/router';

@Directive({ selector: '[routerLink]' })
export class MockRouterLinkDirective {
    @Input('routerLink') routerLink: any;

    constructor(private router: Router) {}

    @HostListener('click')
    onClick() {
        this.router.navigateByUrl(this.routerLink);
    }
}

@Directive({ selector: '[routerLinkActiveOptions]' })
export class MockRouterLinkActiveOptionsDirective {
    @Input('routerLinkActiveOptions') data: any;
}

@Directive({ selector: '[queryParams]' })
export class MockQueryParamsDirective {
    @Input('queryParams') data: any;
}

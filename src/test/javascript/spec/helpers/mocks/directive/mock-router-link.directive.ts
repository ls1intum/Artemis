import { Directive, HostListener, Input } from '@angular/core';
import { Router } from '@angular/router';

@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[routerLink]'
})
export class MockRouterLinkDirective {
    @Input('routerLink') routerLink: any;

    constructor(private router: Router) {}

    @HostListener('click')
    onClick() {
        this.router.navigateByUrl(this.routerLink);
    }
}

@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[routerLinkActiveOptions]'
})
export class MockRouterLinkActiveOptionsDirective {
    @Input('routerLinkActiveOptions') data: any;
}

@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[queryParams]'
})
export class MockQueryParamsDirective {
    @Input('queryParams') data: any;
}

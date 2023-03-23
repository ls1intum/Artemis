import { Directive, HostListener, Input, Optional } from '@angular/core';
import { Router } from '@angular/router';

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[routerLink]',
})
export class MockRouterLinkDirective {
    @Input('routerLink') data: any;

    constructor(@Optional() private router: Router) {}

    @HostListener('click')
    onClick() {
        this.router.navigateByUrl(this.data);
    }
}

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[routerLinkActiveOptions]',
})
export class MockRouterLinkActiveOptionsDirective {
    @Input('routerLinkActiveOptions') data: any;
}

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[queryParams]',
})
export class MockQueryParamsDirective {
    @Input('queryParams') data: any;
}

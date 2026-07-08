import { Directive, HostListener, Optional, input } from '@angular/core';
import { Router } from '@angular/router';

@Directive({
    selector: '[routerLink]',
})
export class MockRouterLinkDirective {
    data = input<any>(undefined, { alias: 'routerLink' });

    constructor(@Optional() private router: Router) {}

    @HostListener('click')
    onClick() {
        this.router.navigateByUrl(this.data());
    }
}

@Directive({
    selector: '[routerLinkActiveOptions]',
})
export class MockRouterLinkActiveOptionsDirective {
    data = input<any>(undefined, { alias: 'routerLinkActiveOptions' });
}

@Directive({
    selector: '[queryParams]',
})
export class MockQueryParamsDirective {
    data = input<any>(undefined, { alias: 'queryParams' });
}

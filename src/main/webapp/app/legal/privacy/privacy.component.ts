import { Component, AfterViewInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-privacy',
    templateUrl: './privacy.component.html',
    styles: [],
})
export class PrivacyComponent implements AfterViewInit {
    constructor(private route: ActivatedRoute) {}

    ngAfterViewInit(): void {
        this.route.params.subscribe(params => {
            try {
                const fragment = document.querySelector('#' + params['fragment']);
                if (fragment !== null) {
                    fragment.scrollIntoView();
                }
            } catch (e) {}
        });
    }
}

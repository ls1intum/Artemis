import { Component, AfterViewInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-privacy',
    templateUrl: './privacy.component.html',
    styles: []
})
export class PrivacyComponent implements AfterViewInit {

    constructor(private route: ActivatedRoute) { }

    ngAfterViewInit(): void {
        this.route.params.subscribe(params => {
            try {
                document.querySelector('#' + params['fragment']).scrollIntoView();
            } catch (e) { }
        });

    }

}

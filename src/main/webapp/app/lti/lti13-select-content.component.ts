import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-select-exercise',
    templateUrl: './lti13-select-content.component.html',
})
export class Lti13SelectContentComponent implements OnInit {
    courseId: number;
    jwt: string;
    id: string;
    actionLink: string;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initializes the component.
     * - Retrieves html response from the route snapshot.
     * - Loads the response.
     */
    ngOnInit(): void {
        this.route.params.subscribe(() => {
            const htmlResponse = this.route.snapshot.queryParamMap.get('htmlResponse') ?? '';
            document.open();
            document.write(htmlResponse);
            document.close();
        });
    }
}

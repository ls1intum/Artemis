import { Component } from '@angular/core';

@Component({
    selector: 'jhi-competency-search',
    templateUrl: './competency-search.component.html',
})
export class CompetencySearchComponent {
    constructor() {}

    reset() {}

    sendUpdate() {}

    courseTitleSearch: string;
    courseSemesterSearch: string;
    competencyDescriptionSearch: string;
}

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CourseCompetencyFilter } from 'app/shared/table/pageable-table';

@Component({
    selector: 'jhi-competency-search',
    template: '',
})
export class CompetencySearchStubComponent {
    @Input() search: CourseCompetencyFilter;
    @Output() searchChange = new EventEmitter<CourseCompetencyFilter>();
}

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CompetencyFilter } from 'app/shared/table/pageable-table';

@Component({
    selector: 'jhi-competency-search',
    template: '',
})
export class CompetencySearchStubComponent {
    @Input() search: CompetencyFilter;
    @Output() searchChange = new EventEmitter<CompetencyFilter>();
}

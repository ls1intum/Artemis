import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Competency } from 'app/entities/competency.model';
import { Component, ContentChild, EventEmitter, Input, Output, TemplateRef } from '@angular/core';

@Component({
    selector: 'jhi-import-competencies-table',
    template: '<div><ng-container *ngTemplateOutlet="buttonsTemplate; context: { competency: {id:1}}" /></div>',
})
export class ImportCompetenciesTableStubComponent {
    @Input() content: SearchResult<Competency>;
    @Input() search: PageableSearch;
    @Input() displayPagination = true;

    @Output() searchChange = new EventEmitter<PageableSearch>();

    @ContentChild(TemplateRef) buttonsTemplate: TemplateRef<any>;
}

import { BasePageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Competency } from 'app/entities/competency.model';
import { Component, ContentChild, EventEmitter, Input, Output, TemplateRef } from '@angular/core';

@Component({
    selector: 'jhi-competency-table',
    template: '<div><ng-container *ngTemplateOutlet="buttonsTemplate; context: { competency: {id:1}}" /></div>',
})
export class CompetencyTableStubComponent {
    @Input() content: SearchResult<Competency>;
    @Input() search: BasePageableSearch;
    @Input() displayPagination = true;

    @Output() searchChange = new EventEmitter<BasePageableSearch>();

    @ContentChild(TemplateRef) buttonsTemplate: TemplateRef<any>;
}

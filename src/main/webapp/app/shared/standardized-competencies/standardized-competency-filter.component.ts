import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { KnowledgeAreaDTO } from 'app/entities/competency/standardized-competency.model';
import { Subject, debounceTime } from 'rxjs';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-standardized-competency-filter',
    templateUrl: './standardized-competency-filter.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective],
})
export class StandardizedCompetencyFilterComponent implements OnInit, OnDestroy {
    @Input() competencyTitleFilter: string;
    @Input() knowledgeAreaFilter?: KnowledgeAreaDTO;
    @Input() knowledgeAreasForSelect: KnowledgeAreaDTO[] = [];

    @Output() competencyTitleFilterChange = new EventEmitter<string>();
    @Output() knowledgeAreaFilterChange = new EventEmitter<KnowledgeAreaDTO>();

    protected titleFilterSubject = new Subject<void>();

    ngOnInit(): void {
        this.titleFilterSubject.pipe(debounceTime(500)).subscribe(() => this.competencyTitleFilterChange.emit(this.competencyTitleFilter));
    }

    ngOnDestroy(): void {
        this.titleFilterSubject.unsubscribe();
    }
}

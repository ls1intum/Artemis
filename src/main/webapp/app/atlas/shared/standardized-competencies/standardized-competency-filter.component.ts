import { Component, DestroyRef, effect, inject, input, model, output } from '@angular/core';
import { KnowledgeAreaDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { Subject, debounceTime } from 'rxjs';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-standardized-competency-filter',
    templateUrl: './standardized-competency-filter.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective],
})
export class StandardizedCompetencyFilterComponent {
    competencyTitleFilter = model<string>();
    knowledgeAreaFilter = model<KnowledgeAreaDTO>();
    knowledgeAreasForSelect = input<KnowledgeAreaDTO[]>([]);

    competencyTitleFilterChange = output<string>();
    knowledgeAreaFilterChange = output<KnowledgeAreaDTO>();

    protected titleFilterSubject = new Subject<void>();

    constructor() {
        effect(() => {
            this.titleFilterSubject.pipe(debounceTime(500)).subscribe(() => this.competencyTitleFilterChange.emit(this.competencyTitleFilter() ?? ''));
        });

        inject(DestroyRef).onDestroy(() => {
            this.titleFilterSubject.unsubscribe();
        });
    }
}

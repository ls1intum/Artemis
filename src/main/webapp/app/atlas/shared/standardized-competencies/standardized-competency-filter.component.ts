import { Component, OnDestroy, OnInit, input, output } from '@angular/core';
import { KnowledgeAreaDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { Subject, debounceTime } from 'rxjs';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-standardized-competency-filter',
    templateUrl: './standardized-competency-filter.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective],
})
export class StandardizedCompetencyFilterComponent implements OnInit, OnDestroy {
    competencyTitleFilter = input<string>('');
    knowledgeAreaFilter = input<KnowledgeAreaDTO | undefined>();
    knowledgeAreasForSelect = input<KnowledgeAreaDTO[]>([]);

    competencyTitleFilterChange = output<string>();
    knowledgeAreaFilterChange = output<KnowledgeAreaDTO | undefined>();

    protected titleFilterSubject = new Subject<string>();

    ngOnInit(): void {
        this.titleFilterSubject.pipe(debounceTime(500)).subscribe((value) => this.competencyTitleFilterChange.emit(value));
    }

    ngOnDestroy(): void {
        this.titleFilterSubject.unsubscribe();
    }
}

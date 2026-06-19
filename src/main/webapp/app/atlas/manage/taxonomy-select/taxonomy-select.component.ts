import { Component, input } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SelectModule } from 'primeng/select';

interface TaxonomyOption {
    value: CompetencyTaxonomy;
    indent: string;
}

@Component({
    selector: 'jhi-taxonomy-select',
    templateUrl: './taxonomy-select.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, SelectModule],
})
export class TaxonomySelectComponent {
    /**
     * id given of the select, to be referenced by labels (for)
     */
    selectId = input<string>('');

    /**
     * Form control for the selected taxonomy
     */
    form = input.required<FormControl>();

    /**
     * Options for the select, with increasing indentation per taxonomy entry.
     * @protected
     */
    protected readonly taxonomyOptions: TaxonomyOption[] = Object.values(CompetencyTaxonomy).map((value, i) => ({
        value,
        indent: '\xA0'.repeat(i),
    }));
}

import { Component, input } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { KeyValuePipe } from '@angular/common';

@Component({
    selector: 'jhi-taxonomy-select',
    templateUrl: './taxonomy-select.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, KeyValuePipe],
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
     * increasing indentation for the select options
     * @protected
     */
    protected readonly indent = Object.keys(CompetencyTaxonomy).map((_, i) => '\xA0'.repeat(i));

    protected readonly competencyTaxonomy = CompetencyTaxonomy;

    /**
     * Keeps order of elements as-is in the keyvalue pipe
     */
    keepOrder = () => {
        return 0;
    };
}

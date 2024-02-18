import { Component, Input } from '@angular/core';
import { FormControl } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-taxonomy-select',
    templateUrl: './taxonomy-select.component.html',
})
export class TaxonomySelectComponent {
    /**
     * id given of the select, to be referenced by labels (for)
     */
    @Input() selectId = '';

    /**
     * Form control for the selected taxonomy
     */
    @Input({ required: true }) form: FormControl;

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

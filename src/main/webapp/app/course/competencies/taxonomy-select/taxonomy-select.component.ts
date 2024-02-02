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
    @Input() selectId: string;

    /**
     * Form control for the selected taxonomy
     */
    @Input() form: FormControl<CompetencyTaxonomy | undefined>;

    protected readonly competencyTaxonomy = CompetencyTaxonomy;

    /**
     * Keeps order of elements as-is in the keyvalue pipe
     */
    keepOrder = () => {
        return 0;
    };
}

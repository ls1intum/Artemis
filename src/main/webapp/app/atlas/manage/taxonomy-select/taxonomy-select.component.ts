import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiSelectComponent } from '@tumaet/ui-angular';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';

interface TaxonomyOption {
    value: CompetencyTaxonomy;
    label: string;
}

@Component({
    selector: 'jhi-taxonomy-select',
    templateUrl: './taxonomy-select.component.html',
    imports: [FormsModule, ReactiveFormsModule, TumAetUiSelectComponent],
})
export class TaxonomySelectComponent {
    private readonly translateService = inject(TranslateService);
    private readonly languageChange = toSignal(this.translateService.onLangChange, { initialValue: undefined });

    /**
     * id given of the select, to be referenced by labels (for)
     */
    selectId = input<string>('');

    /**
     * Form control for the selected taxonomy
     */
    form = input.required<FormControl>();

    /**
     * Options for the select: the translated taxonomy followed by its keywords, rebuilt when the language changes.
     * @protected
     */
    protected readonly taxonomyOptions = computed<TaxonomyOption[]>(() => {
        this.languageChange();
        return Object.values(CompetencyTaxonomy).map((value) => ({
            value,
            label: `${this.translateService.instant('artemisApp.courseCompetency.taxonomies.' + value)} (${this.translateService.instant('artemisApp.courseCompetency.keywords.' + value)})`,
        }));
    });
}

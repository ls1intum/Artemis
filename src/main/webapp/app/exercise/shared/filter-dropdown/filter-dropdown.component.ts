import { Component, ViewEncapsulation, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SelectModule } from 'primeng/select';

@Component({
    selector: 'jhi-filter-dropdown',
    templateUrl: './filter-dropdown.component.html',
    styleUrls: ['./filter-dropdown.component.scss'],
    imports: [FormsModule, ArtemisTranslatePipe, SelectModule],
    encapsulation: ViewEncapsulation.None,
})
export class FilterDropdownComponent {
    readonly filters = input.required<string[]>();
    readonly activeFilter = input.required<string>();
    /** The value that represents the "show all" state — used to decide button style and label. */
    readonly allValue = input<string>('All');
    readonly filterChange = output<string>();

    readonly isFiltered = computed(() => this.activeFilter() !== this.allValue());
}

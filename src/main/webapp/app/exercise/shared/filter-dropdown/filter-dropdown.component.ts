import { Component, input, output } from '@angular/core';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-filter-dropdown',
    templateUrl: './filter-dropdown.component.html',
    styleUrls: ['./filter-dropdown.component.scss'],
    imports: [NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, FaIconComponent, FormsModule, ArtemisTranslatePipe],
})
export class FilterDropdownComponent {
    readonly filters = input.required<string[]>();
    readonly activeFilter = input.required<string>();
    /** The value that represents the "show all" state — used to decide button style and label. */
    readonly allValue = input<string>('All');
    readonly filterChange = output<string>();

    protected readonly faFilter = faFilter;
}

import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SearchableEntity } from '../global-search-modal.component';

@Component({
    selector: 'jhi-global-search-entity-item',
    standalone: true,
    imports: [FaIconComponent],
    templateUrl: './searchable-entity-item.component.html',
    styleUrls: ['./searchable-entity-item.component.scss'],
})
export class SearchableEntityItemComponent {
    // Inputs
    entity = input.required<SearchableEntity>();
    isSelected = input.required<boolean>();

    // Outputs
    entityClick = output<SearchableEntity>();

    protected onClick() {
        this.entityClick.emit(this.entity());
    }
}

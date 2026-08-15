import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faLevelDownAlt } from '@fortawesome/free-solid-svg-icons';
import { SearchableEntity } from '../../../models/searchable-entity.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-global-search-entity-item',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './searchable-entity-item.component.html',
    styleUrls: ['./searchable-entity-item.component.scss'],
})
export class SearchableEntityItemComponent {
    entity = input.required<SearchableEntity>();
    isSelected = input.required<boolean>();

    entityClick = output<SearchableEntity>();

    protected readonly faLevelDownAlt = faLevelDownAlt;

    protected onClick() {
        this.entityClick.emit(this.entity());
    }
}

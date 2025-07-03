import { Component, model } from '@angular/core';
import { MemirisGraphFilters } from '../entities/memiris.model';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faBookOpen, faBrain, faCircleNodes, faTrash } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-memiris-graph-filters',
    imports: [FormsModule, FontAwesomeModule],
    templateUrl: './memiris-graph-filters.component.html',
    styleUrl: './memiris-graph-filters.component.scss',
})
export class MemirisGraphFiltersComponent {
    filters = model(new MemirisGraphFilters());

    faBrain = faBrain;
    faBookOpen = faBookOpen;
    faCircleNodes = faCircleNodes;
    faTrash = faTrash;

    updateFilter(event: any, attribute: keyof MemirisGraphFilters) {
        this.filters.update((previous) => {
            const current = { ...previous };
            current[attribute] = event.target.checked ?? current[attribute];
            return current;
        });
    }
}

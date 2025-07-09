import { Component, model } from '@angular/core';
import { MemirisGraphSettings } from '../entities/memiris.model';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faBookOpen, faBrain, faCircleNodes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-memiris-graph-settings',
    imports: [FormsModule, FontAwesomeModule, ArtemisTranslatePipe],
    templateUrl: './memiris-graph-settings.component.html',
    styleUrl: './memiris-graph-settings.component.scss',
})
export class MemirisGraphSettingsComponent {
    settings = model(new MemirisGraphSettings());

    faBrain = faBrain;
    faBookOpen = faBookOpen;
    faCircleNodes = faCircleNodes;
    faTrash = faTrash;

    updateSetting(event: any, attribute: keyof MemirisGraphSettings) {
        this.settings.update((previous) => {
            const current = { ...previous };
            current[attribute] = event.target.checked ?? current[attribute];
            return current;
        });
    }
}

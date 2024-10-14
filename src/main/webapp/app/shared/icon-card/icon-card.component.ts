import { Component, input } from '@angular/core';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-common-types';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';

@Component({
    selector: 'jhi-icon-card',
    templateUrl: './icon-card.component.html',
    styleUrl: './icon-card.component.scss',
    standalone: true,
    imports: [ArtemisSharedCommonModule, ArtemisSharedPipesModule],
})
export class IconCardComponent {
    headerIcon = input<IconDefinition>(faCircleInfo);

    headline = input<string>('Title');
}

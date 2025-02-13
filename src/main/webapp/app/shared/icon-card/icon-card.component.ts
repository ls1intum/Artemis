import { Component, input } from '@angular/core';
import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-common-types';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-icon-card',
    templateUrl: './icon-card.component.html',
    styleUrl: './icon-card.component.scss',
    imports: [TranslateDirective, FontAwesomeModule],
})
export class IconCardComponent {
    headerIcon = input<IconDefinition>(faCircleInfo);

    headline = input<string>('Title');
}

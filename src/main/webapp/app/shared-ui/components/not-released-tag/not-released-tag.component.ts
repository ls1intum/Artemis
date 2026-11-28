import { Component, input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTagComponent } from '@tumaet/ui-angular/tag';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-not-released-tag',
    templateUrl: './not-released-tag.component.html',
    styleUrls: ['./not-released-tag.component.css'],
    imports: [TranslateDirective, TumAetUiTagComponent, TumAetUiTooltipDirective, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class NotReleasedTagComponent {
    exercise = input.required<Exercise>();
    noMargin = input(true);
    readonly dayjs = dayjs;
}

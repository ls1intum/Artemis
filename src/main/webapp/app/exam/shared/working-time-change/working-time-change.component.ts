import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-working-time-change',
    templateUrl: './working-time-change.component.html',
    standalone: true,
    imports: [TranslateDirective, ArtemisSharedCommonModule],
})
export class WorkingTimeChangeComponent {
    oldWorkingTime = input.required<number>();
    newWorkingTime = input.required<number>();
}

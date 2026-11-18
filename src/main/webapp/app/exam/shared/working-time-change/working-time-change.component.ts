import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-working-time-change',
    templateUrl: './working-time-change.component.html',
    imports: [TranslateDirective, ArtemisDurationFromSecondsPipe],
})
export class WorkingTimeChangeComponent {
    oldWorkingTime = input.required<number>();
    newWorkingTime = input.required<number>();
}

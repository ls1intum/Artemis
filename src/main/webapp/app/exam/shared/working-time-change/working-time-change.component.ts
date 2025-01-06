import { Component, Input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-working-time-change',
    templateUrl: './working-time-change.component.html',
    imports: [TranslateDirective, ArtemisDurationFromSecondsPipe],
})
export class WorkingTimeChangeComponent {
    @Input() oldWorkingTime: number;
    @Input() newWorkingTime: number;
}

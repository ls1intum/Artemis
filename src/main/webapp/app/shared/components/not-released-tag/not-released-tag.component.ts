import { Component, Input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-not-released-tag',
    templateUrl: './not-released-tag.component.html',
    styleUrls: ['./not-released-tag.component.scss'],
    imports: [TranslateDirective, NgbTooltip, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class NotReleasedTagComponent {
    @Input() public exercise: Exercise;
    @Input() public noMargin = true;
    readonly dayjs = dayjs;
}

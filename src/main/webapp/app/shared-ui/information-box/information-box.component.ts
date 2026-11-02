import { Component, computed, input } from '@angular/core';

import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { DateType } from 'app/foundation/pipes/artemis-date.pipe';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CommonModule } from '@angular/common';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';

export interface InformationBox {
    title: string;
    content: InformationBoxContent;
    isContentComponent?: boolean;
    tooltip?: string;
    tooltipParams?: Record<string, string | undefined>;
    contentColor?: string;
}

export interface StudentExamContent {
    type: 'workingTime';
    value: StudentExam;
}

export interface DateContent {
    type: 'timeAgo' | 'dateTime';
    value: DateType;
}

export interface DifficultyLevelContent {
    type: 'difficultyLevel';
    value: DifficultyLevel;
}

export interface ExerciseContent {
    type: 'submissionStatus' | 'categories';
    value: Exercise;
}

export interface StringNumberContent {
    type: 'string';
    value: string | number;
}

export type InformationBoxContent = StudentExamContent | DateContent | ExerciseContent | DifficultyLevelContent | StringNumberContent;

/**
 * How a box arranges its label and its value.
 *
 * `stacked` is the default: the label above the value, as the boxes read in a details panel. `inline` puts the two on
 * one line at a title bar's control height, for the boxes the exercise title bar takes over when it has the room.
 */
export type InformationBoxLayout = 'stacked' | 'inline';

@Component({
    imports: [ArtemisTranslatePipe, TranslateDirective, CommonModule, TumAetUiTooltipDirective],
    selector: 'jhi-information-box',
    templateUrl: './information-box.component.html',
    styleUrls: ['./information-box.component.scss'],
})
export class InformationBoxComponent {
    informationBoxData = input<InformationBox>();
    readonly layout = input<InformationBoxLayout>('stacked');
    protected readonly contentClass = computed(() => {
        switch (this.informationBoxData()?.contentColor) {
            case 'danger':
                return 'text-state-danger';
            case 'warning':
                return 'text-state-warning';
            case 'success':
                return 'text-state-success';
            case 'info':
                return 'text-state-info';
            case 'primary':
                return 'text-primary';
            case 'secondary':
                return 'text-muted-color';
            default:
                return 'text-(--tumaet-ui-text-color)';
        }
    });
}

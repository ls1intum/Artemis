import { Component, input } from '@angular/core';

import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { DateType } from '../pipes/artemis-date.pipe';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

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

@Component({
    imports: [ArtemisTranslatePipe, TranslateDirective, CommonModule, NgbTooltipModule],
    selector: 'jhi-information-box',
    templateUrl: './information-box.component.html',
    styleUrls: ['./information-box.component.scss'],
})
export class InformationBoxComponent {
    informationBoxData = input<InformationBox>();
}

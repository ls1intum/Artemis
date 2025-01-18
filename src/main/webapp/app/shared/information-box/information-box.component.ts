import { Component, Input } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { DifficultyLevel, Exercise } from 'app/entities/exercise.model';
import { DateType } from '../pipes/artemis-date.pipe';
import { StudentExam } from 'app/entities/student-exam.model';

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
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    selector: 'jhi-information-box',
    templateUrl: './information-box.component.html',
    styleUrls: ['./information-box.component.scss'],
})
export class InformationBoxComponent {
    @Input() informationBoxData: InformationBox;
}

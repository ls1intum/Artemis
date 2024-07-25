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

interface StudentExamContent {
    type: 'workingTime';
    value: StudentExam;
}

interface DateContent {
    type: 'timeAgo' | 'dateTime';
    value: DateType;
}

interface DifficultyLevelContent {
    type: 'difficultyLevel';
    value: DifficultyLevel;
}

interface ExerciseContent {
    type: 'submissionStatus' | 'categories';
    value: Exercise;
}

interface StringContent {
    type: 'string';
    value: string;
}

export type InformationBoxContent = StudentExamContent | DateContent | ExerciseContent | DifficultyLevelContent | StringContent;

@Component({
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    selector: 'jhi-information-box',
    templateUrl: './information-box.component.html',
    styleUrls: ['./information-box.component.scss'],
})
export class InformationBoxComponent {
    @Input() informationBoxData: InformationBox;
}

import { Component, computed, input, model } from '@angular/core';
import { ExamType, isTestExamType } from 'app/exam/shared/entities/exam.model';
import { ExerciseTimelineComponent, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { Dayjs } from 'dayjs/esm';
import { InputNumber } from 'primeng/inputnumber';
import { FormsModule } from '@angular/forms';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exam-timeline',
    imports: [ExerciseTimelineComponent, InputNumber, FormsModule, HelpIconComponent, TranslateDirective],
    templateUrl: './exam-timeline.component.html',
})
export class ExamTimelineComponent {
    readonly examType = input.required<ExamType>();

    readonly visibleFrom = model.required<Dayjs | undefined>();
    readonly startOfWorkingTime = model.required<Dayjs | undefined>();
    readonly endOfSimulationTime = computed(() => (this.examType() === 'SIMULATION_AND_PRACTICE' ? this.startOfWorkingTime()?.add(this.workingTime() ?? 0, 'seconds') : undefined));
    readonly startOfPracticeTime = model.required<Dayjs | undefined>();
    readonly endOfWorkingTime = model.required<Dayjs | undefined>();

    workingTime = model.required<number | undefined>(); // seconds
    gracePeriod = model.required<number | undefined>(); // seconds

    readonly timelineItems = computed(() => {
        const examType = this.examType();
        const isTestExam = isTestExamType(examType);

        const simulationEndAndPracticeStart: TimelineItem[] =
            this.examType() === 'SIMULATION_AND_PRACTICE'
                ? [
                      {
                          kind: 'derived',
                          labelStringKey: 'artemisApp.examManagement.testExam.simulationEndDate',
                          date: this.endOfSimulationTime,
                          mustBeStrictlyAfterPrevious: true,
                      },
                      {
                          kind: 'required',
                          labelStringKey: 'artemisApp.examManagement.testExam.practiceStartDate',
                          date: this.startOfPracticeTime,
                          mustBeStrictlyAfterPrevious: true,
                          helpKey: 'artemisApp.examManagement.testExam.practiceStartDateTooltip',
                      },
                  ]
                : [];

        const items: TimelineItem[] = [
            {
                kind: 'required',
                labelStringKey: 'artemisApp.examManagement.visibleDate',
                date: this.visibleFrom,
                helpKey: 'artemisApp.examManagement.visibleDateTooltip',
            },
            {
                kind: 'required',
                labelStringKey: isTestExam ? 'artemisApp.examManagement.testExam.startDate' : 'artemisApp.examManagement.startDate',
                date: this.startOfWorkingTime,
                mustBeStrictlyAfterPrevious: true,
                helpKey: 'artemisApp.examManagement.startDateTooltip',
            },
            ...simulationEndAndPracticeStart,
            {
                kind: 'required',
                labelStringKey: isTestExam ? 'artemisApp.examManagement.testExam.endDate' : 'artemisApp.examManagement.endDate',
                date: this.endOfWorkingTime,
                mustBeStrictlyAfterPrevious: true,
                helpKey: 'artemisApp.examManagement.endDateTooltip',
            },
        ];
        return items;
    });

    readonly usesExamWindowWorkingTime = computed(() => {
        const examType = this.examType();
        return !isTestExamType(examType) || examType === ExamType.SIMULATION;
    });
}

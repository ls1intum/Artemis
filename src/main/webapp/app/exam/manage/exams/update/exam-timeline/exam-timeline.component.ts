import { Component, computed, effect, input, model, output, signal } from '@angular/core';
import { ExamType, isTestExamType } from 'app/exam/shared/entities/exam.model';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { Dayjs } from 'dayjs/esm';
import { InputNumber } from 'primeng/inputnumber';
import { FormsModule } from '@angular/forms';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Message } from 'primeng/message';
import { normalWorkingTime } from 'app/exam/overview/exam.utils';

@Component({
    selector: 'jhi-exam-timeline',
    imports: [ExerciseTimelineComponent, InputNumber, FormsModule, HelpIconComponent, TranslateDirective, Message],
    templateUrl: './exam-timeline.component.html',
})
export class ExamTimelineComponent {
    readonly max_working_time_in_minutes = 43200 as const;
    readonly max_grace_period_in_seconds = 3600 as const;

    readonly examType = input.required<ExamType>();

    readonly visibleFrom = model.required<Dayjs | undefined>();
    readonly startOfWorkingTime = model.required<Dayjs | undefined>();
    readonly endOfSimulationTime = computed(() => (this.examType() === 'SIMULATION_AND_PRACTICE' ? this.startOfWorkingTime()?.add(this.workingTime() ?? 0, 'seconds') : undefined));
    readonly startOfPracticeTime = model.required<Dayjs | undefined>();
    readonly endOfWorkingTime = model.required<Dayjs | undefined>();

    readonly workingTime = model.required<number | undefined>(); // seconds
    gracePeriod = model.required<number | undefined>(); // seconds

    readonly examTimelineStatusChange = output<boolean>();

    constructor() {
        effect(() => {
            this.examTimelineStatusChange.emit(this.isExamTimelineValid());
        });

        effect(() => {
            this.workingTime.update((workingTime) => (this.noWorkingTimeNeeded() ? (normalWorkingTime(this.startOfWorkingTime(), this.endOfWorkingTime()) ?? 0) : workingTime));
        });
    }

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

    readonly noWorkingTimeNeeded = computed(() => {
        const examType = this.examType();
        return !isTestExamType(examType) || examType === ExamType.SIMULATION;
    });

    readonly maxWorkingTimeInMinutes = computed(() => {
        const startOfWorkingTime = this.startOfWorkingTime();
        const endOfWorkingTime = this.endOfWorkingTime();
        if (this.noWorkingTimeNeeded() || !startOfWorkingTime || !endOfWorkingTime) {
            return this.max_working_time_in_minutes;
        }
        return Math.max(0, Math.min(this.max_working_time_in_minutes, endOfWorkingTime.diff(startOfWorkingTime, 'minute', true)));
    });

    readonly isWorkingTimeValid = computed(() => {
        const workingTime = this.workingTime() ?? 0;
        if (workingTime > this.maxWorkingTimeInMinutes() * 60) {
            return false;
        }
        if (this.noWorkingTimeNeeded()) {
            return true;
        }
        const startOfWorkingTime = this.startOfWorkingTime();
        const endOfWorkingTime = this.endOfWorkingTime();
        return workingTime > 0 && (!startOfWorkingTime || !endOfWorkingTime || workingTime <= endOfWorkingTime.diff(startOfWorkingTime, 'second'));
    });

    readonly isGracePeriodValid = computed(() => {
        const gracePeriod = this.gracePeriod();
        return gracePeriod === undefined || (gracePeriod >= 0 && gracePeriod <= this.max_grace_period_in_seconds);
    });

    readonly timelineStatus = signal<ExerciseTimelineStatus>({ valid: false, empty: false });

    private readonly isExamTimelineValid = computed(() => this.timelineStatus().valid && this.isWorkingTimeValid() && this.isGracePeriodValid());

    readonly showVisibleFromWarning = computed(() => {
        const visibleFrom = this.visibleFrom();
        const startOfWorkingTime = this.startOfWorkingTime();

        if (!visibleFrom || !startOfWorkingTime) {
            return false;
        }

        // Calculate the difference in minutes
        const differenceInMinutes = startOfWorkingTime.diff(visibleFrom, 'minute');

        // Check if the difference is more than 4 hours (240 minutes)
        return differenceInMinutes > 240;
    });
}

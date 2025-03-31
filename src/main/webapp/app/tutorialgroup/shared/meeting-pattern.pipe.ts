import { Pipe, PipeTransform } from '@angular/core';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TranslateService } from '@ngx-translate/core';
import { RemoveSecondsPipe } from 'app/tutorialgroup/shared/remove-seconds.pipe';
import { getDayTranslationKey } from 'app/tutorialgroup/shared/weekdays';

/**
 * A pipe that generates a translated meeting pattern given a tutorial group schedule
 * Example: 'Every Week, Monday from 14:00 to 15:00' English
 * Example: 'Jede Woche, Montag von 14:00 bis 15:00' German
 */
@Pipe({ name: 'meetingPattern' })
export class MeetingPatternPipe implements PipeTransform {
    removeSecondsPipe = new RemoveSecondsPipe();

    constructor(private translateService: TranslateService) {}

    /**
     * Transforms a tutorial group schedule to a translated meeting pattern.
     * @param schedule The tutorial group schedule to transform.
     * @returns The translated meeting pattern.
     */
    transform(schedule: TutorialGroupSchedule | undefined, includeRepetitionFrequency = false): string {
        if (!schedule) {
            return '';
        }

        const weekDayTranslated = this.translateService.instant(getDayTranslationKey(schedule.dayOfWeek));
        const repetitionTranslated = includeRepetitionFrequency ? `${this.getRepetitionTranslated(schedule.repetitionFrequency)}, ` : '';
        const startTime = this.removeSecondsPipe.transform(schedule.startTime);
        const endTime = this.removeSecondsPipe.transform(schedule.endTime);
        return `${repetitionTranslated}${weekDayTranslated}, ${startTime} - ${endTime}`;
    }

    private getRepetitionTranslated(repetitionFrequency?: number) {
        if (!repetitionFrequency) {
            return '';
        } else if (repetitionFrequency === 1) {
            return this.translateService.instant(`artemisApp.generic.repetitions.everyWeek`);
        } else {
            return this.translateService.instant(`artemisApp.generic.repetitions.everyNWeeks`, { n: repetitionFrequency });
        }
    }
}

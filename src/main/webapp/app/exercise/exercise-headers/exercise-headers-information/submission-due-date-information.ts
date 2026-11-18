import dayjs from 'dayjs/esm';
import { InformationBox } from 'app/shared-ui/information-box/information-box.component';
import { isDateLessThanAWeekInTheFuture } from 'app/foundation/util/date.utils';

export function getSubmissionDueDateInformation(dueDate: dayjs.Dayjs | undefined, now = dayjs()): InformationBox | undefined {
    if (!dueDate) {
        return undefined;
    }
    const isDueDateInThePast = dueDate.isBefore(now);
    // If the due date is less than a day away, the color change to red
    const dueDateStatusBadge = dueDate.isBetween(now, now.add(1, 'day')) ? 'danger' : 'body-color';
    // If the due date is less than a week away, text is displayed relatively e.g. 'in 2 days'
    const shouldDisplayDueDateRelative = isDateLessThanAWeekInTheFuture(dueDate, now);

    if (isDueDateInThePast) {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.submissionDueOver',
            content: {
                type: 'dateTime',
                value: dueDate,
            },
            isContentComponent: true,
        };
    }

    return {
        title: 'artemisApp.courseOverview.exerciseDetails.submissionDue',
        content: {
            type: shouldDisplayDueDateRelative ? 'timeAgo' : 'dateTime',
            value: dueDate,
        },
        isContentComponent: true,
        tooltip: shouldDisplayDueDateRelative ? 'artemisApp.courseOverview.exerciseDetails.submissionDueTooltip' : undefined,
        contentColor: dueDateStatusBadge,
        tooltipParams: { date: dueDate.format('lll') },
    };
}

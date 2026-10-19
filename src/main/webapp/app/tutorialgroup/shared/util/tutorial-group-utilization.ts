import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

/** Multiplier turning a ratio into a percentage. */
const PERCENTAGE_SCALE = 100;

/** Utilization below which a group counts as under-attended. */
export const UNDER_ATTENDED_PERCENTAGE = 25;

/** Utilization from which on a group counts as well attended. */
export const WELL_UTILIZED_PERCENTAGE = 50;

/**
 * Average attendance of the last three sessions as a percentage of the group's capacity.
 * @param tutorialGroup the group to measure
 * @returns the rounded percentage, or undefined when attendance or capacity is unknown
 */
export function tutorialGroupUtilization(tutorialGroup: TutorialGroup): number | undefined {
    const { averageAttendance, capacity } = tutorialGroup;
    if (averageAttendance === undefined || !capacity) {
        return undefined;
    }
    return Math.round((averageAttendance / capacity) * PERCENTAGE_SCALE);
}

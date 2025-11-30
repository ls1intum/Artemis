/**
 * Types of events that can be logged for scientific purposes.
 *
 * Important: Please follow the naming convention <category>__<detailed event name>
 */
export enum ScienceEventType {
    LECTURE__OPEN = 'LECTURE__OPEN',
    LECTURE__OPEN_UNIT = 'LECTURE__OPEN_UNIT',
    EXERCISE__OPEN = 'EXERCISE__OPEN',
    COMPETENCY__OPEN = 'COMPETENCY__OPEN',
    COMPETENCY__OPEN_OVERVIEW = 'COMPETENCY__OPEN_OVERVIEW',
    LEARNING_PATH__OPEN = 'LEARNING_PATH__OPEN',
    LEARNING_PATH__OPEN_NAVIGATION = 'LEARNING_PATH__OPEN_NAVIGATION',
    LEARNING_PATH__NAV_NEXT = 'LEARNING_PATH__NAV_NEXT',
    LEARNING_PATH__NAV_PREV = 'LEARNING_PATH__NAV_PREV',
    LEARNING_PATH__OPEN_GRAPH = 'LEARNING_PATH__OPEN_GRAPH',
    THEIA__OPEN = 'THEIA__OPEN',
}

export class ScienceEventDTO {
    type?: ScienceEventType;
    resourceId?: number;
}

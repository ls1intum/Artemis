/**
 * Types of events that can be logged for scientific purposes.
 *
 * Important: Please follow the naming convention <category>__<detailed event name>
 */
export enum ScienceEventType {
    LECTURE__OPEN = 'LECTURE__OPEN',
}

export class ScienceEventDTO {
    type?: ScienceEventType;
    resourceId?: number;
}

/**
 * Types of events that can be logged for scientific purposes.
 *
 * Important: Please follow the naming convention <category>__<detailed event name>
 */
export enum ScienceEventType {
    LECTURE__OPEN = 'LECTURE__OPEN',
    LECTURE__TEXT_UNIT_OPEN = 'LECTURE__TEXT_UNIT_OPEN',
    LECTURE__VIDEO_UNIT_OPEN = 'LECTURE__VIDEO_UNIT_OPEN',
    LECTURE__FILE_UNIT_DOWNLOAD = 'LECTURE__FILE_UNIT_DOWNLOAD',
    LECTURE__ONLINE_UNIT_OPEN = 'LECTURE__ONLINE_UNIT_OPEN',
}

export class ScienceEventDTO {
    type?: ScienceEventType;
    resourceId?: number;
}

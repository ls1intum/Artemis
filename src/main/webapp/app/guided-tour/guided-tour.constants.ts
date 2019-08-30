export interface OrientationConfiguration {
    /** Where the tour step will appear next to the selected element */
    orientationDirection: Orientation;
    /** When this orientation configuration starts in pixels */
    maximumSize?: number;
}

/* Orientation of the tour step position next to the highlighted element */
export enum Orientation {
    BOTTOM = 'bottom',
    BOTTOMLEFT = 'bottom-left',
    BOTTOMRIGHT = 'bottom-right',
    CENTER = 'center',
    LEFT = 'left',
    RIGHT = 'right',
    TOP = 'top',
    TOPLEFT = 'top-left',
    TOPRIGHT = 'top-right',
}

/* Content type of tour step content */
export enum ContentType {
    TEXT,
    IMAGE,
    VIDEO,
}

/* Link type of the link within the tour step content */
export enum LinkType {
    LINK,
    BUTTON,
}

export enum GuidedTourState {
    STARTED,
    FINISHED,
}

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

export enum GuidedTourState {
    STARTED,
    FINISHED,
}

export enum OverlayPosition {
    TOP = 'top',
    LEFT = 'left',
    RIGHT = 'right',
    BOTTOM = 'bottom',
    ELEMENT = 'element',
}

export enum UserInteractionEvent {
    ACE_EDITOR = 'ace-editor',
    ASSESS_SUBMISSION = 'assess-submission',
    CLICK = 'click',
    MODELING = 'modeling',
    WAIT_FOR_SELECTOR = 'wait-for-selector',
}

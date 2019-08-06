export interface OrientationConfiguration {
    /** Where the tour step will appear next to the selected element */
    orientationDirection: Orientation;
    /** When this orientation configuration starts in pixels */
    maximumSize?: number;
}

/* Orientation of the tour step position next to the highlighted element */
export enum Orientation {
    Bottom = 'bottom',
    BottomLeft = 'bottom-left',
    BottomRight = 'bottom-right',
    Center = 'center',
    Left = 'left',
    Right = 'right',
    Top = 'top',
    TopLeft = 'top-left',
    TopRight = 'top-right',
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

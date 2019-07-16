export interface TourStep {
    /** Selector for element that will be highlighted */
    selector?: string | undefined;
    /** Translation key for the title **/
    headlineTranslateKey: string;
    /** Translation key for the title **/
    subHeadlineTranslateKey?: string;
    /** Define whether only text or text/image or text/video should be included as the tour content **/
    contentType: ContentType;
    /** Translation key for the content **/
    contentTranslateKey: string;
    /** External url **/
    externalUrl?: string;
    /** Text for external url **/
    externalUrlTranslateKey?: string;
    /** Link type, either link or button **/
    linkType?: LinkType;
    /** Image url **/
    imageUrl?: string;
    /** Embed video url **/
    videoUrl?: string;
    /** Where the tour step will appear next to the selected element */
    orientation?: Orientation | OrientationConfiguration[] | undefined;
    /** Action that happens when the step is opened */
    action?: () => void;
    /** Action that happens when the step is closed */
    closeAction?: () => void;
    /** Skips this step, this is so you do not have create multiple tour configurations based on user settings/configuration */
    skipStep?: boolean;
    /** Adds some padding for things like sticky headers when scrolling to an element */
    scrollAdjustment?: number;
    /** Adds default padding around tour highlighting. Does not need to be true for highlightPadding to work */
    useHighlightPadding?: boolean;
    /** Adds padding around tour highlighting in pixels, this overwrites the default for this step. Is not dependent on useHighlightPadding being true */
    highlightPadding?: number;
    /** Permission to view step, if no permission is set then the tour step is visible to ROLE_USER
     * Possible inputs: 'ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'
     */
    permission?: string[];
}

export interface GuidedTour {
    /** Identifier for tour */
    settingsId: string;
    /** Use orb to start tour */
    useOrb?: boolean;
    /** Steps fo the tour */
    steps: TourStep[];
    /** Function will be called when tour is skipped */
    skipCallback?: (stepSkippedOn: number) => void;
    /** Function will be called when tour is completed */
    completeCallback?: () => void;
    /** Minimum size of screen in pixels before the tour is run, if the tour is resized below this value the user will be told to resize */
    minimumScreenSize?: number;
    /**
     * Prevents the tour from advancing by clicking the backdrop.
     * This should only be set if you are completely sure your tour is displaying correctly on all screen sizes otherwise a user can get stuck.
     */
    preventBackdropFromAdvancing?: boolean;
}

export interface OrientationConfiguration {
    /** Where the tour step will appear next to the selected element */
    orientationDirection: Orientation;
    /** When this orientation configuration starts in pixels */
    maximumSize?: number;
}

export class Orientation {
    public static readonly Bottom = 'bottom';
    public static readonly BottomLeft = 'bottom-left';
    public static readonly BottomRight = 'bottom-right';
    public static readonly Center = 'center';
    public static readonly Left = 'left';
    public static readonly Right = 'right';
    public static readonly Top = 'top';
    public static readonly TopLeft = 'top-left';
    public static readonly TopRight = 'top-right';
}

export enum ContentType {
    TEXT,
    IMAGE,
    VIDEO,
}

export enum LinkType {
    LINK,
    BUTTON,
}

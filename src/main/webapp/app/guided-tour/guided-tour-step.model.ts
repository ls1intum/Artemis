import { ContentType, LinkType, Orientation, OrientationConfiguration } from 'app/guided-tour/guided-tour.constants';

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

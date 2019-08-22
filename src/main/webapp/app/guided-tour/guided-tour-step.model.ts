import { LinkType, Orientation, OrientationConfiguration } from 'app/guided-tour/guided-tour.constants';

export abstract class TourStep {
    /** Selector for element that will be highlighted */
    selector?: string | undefined;
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
    /** Adds padding around tour highlighting in pixels, this overwrites the default for this step. Is not dependent on useHighlightPadding being true */
    highlightPadding?: number;
    /** Permission to view step, if no permission is set then the tour step is visible to ROLE_USER
     * Possible inputs: 'ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'
     */
    permission?: string[];

    constructor(data: any) {
        Object.assign(this, data);
    }
}

export class TextTourStep extends TourStep {
    /** Translation key for the title **/
    headlineTranslateKey: string;
    /** Translation key for the title **/
    subHeadlineTranslateKey: string;
    /** Translation key for the content **/
    contentTranslateKey: string;
}

export class TextLinkTourStep extends TextTourStep {
    /** External url **/
    externalUrl: string;
    /** Text for external url **/
    externalUrlTranslateKey: string;
    /** Link type, either link or button **/
    linkType: LinkType;
}

export class ImageTourStep extends TextTourStep {
    /** Image url **/
    imageUrl: string;
}

export class VideoTourStep extends TextTourStep {
    /** Embed video url **/
    videoUrl: string;
}

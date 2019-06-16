import { Orientation } from 'ngx-guided-tour';

interface GuidedTour {
    tourId: string;
    useOrb?: boolean;
    steps: TourStep[];
    skipCallback?: (stepSkippedOn: number) => void;
    completeCallback?: () => void;
    minimumScreenSize?: number;
}

interface TourStep {
    selector?: string;
    title?: string;
    content: string;
    orientation?: Orientation | OrientationConfiguration[];
    action?: () => void;
    closeAction?: () => void;
    skipStep?: boolean;
    scrollAdjustment?: number;
    useHighlightPadding?: boolean;
    highlightPadding?: number;
}

interface OrientationConfiguration {
    orientationDirection: Orientation;
    maximumSize?: number;
}

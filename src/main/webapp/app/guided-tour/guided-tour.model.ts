import { TourStep } from 'app/guided-tour/guided-tour-step.model';

export interface GuidedTour {
    /** Identifier for tour */
    settingsKey: string;
    /** Steps fo the tour */
    steps: TourStep[];
    /** The given function will be called when tour is skipped */
    skipCallback?: (stepSkippedOn: number) => void;
    /** The given function will be called when tour is completed */
    completeCallback?: () => void;
    /** Minimum size of screen in pixels before the tour is run, if the tour is resized below this value the user will be told to resize */
    minimumScreenSize?: number;
    /**
     * Prevents the tour from advancing by clicking the backdrop.
     * This should only be set if you are completely sure your tour is displaying correctly on all screen sizes otherwise a user can get stuck.
     */
    preventBackdropFromAdvancing?: boolean;
}

import { TourStep } from 'app/guided-tour/guided-tour-step.model';

export interface GuidedTour {
    /** Short name of the course for which the tour should be displayed,
     * if the tour display is not limited to any course then leave an empty string
     * */
    courseShortName: string;
    /** Short name of the programming exercise for which the tour should be displayed
     *  if the tour display is not limited to any exercise then leave an empty string
     *  */
    exerciseShortName: string;
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
    /** Defines if the UML model in the apollon editor should be resetted if the user restarts the tutorial */
    resetUMLModel?: boolean;
}

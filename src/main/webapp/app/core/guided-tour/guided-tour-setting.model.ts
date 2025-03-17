import { GuidedTourState } from 'app/core/guided-tour/guided-tour.constants';

/**
 * Guided tour settings model which can dynamically handle the existing guided tours
 */
export class GuidedTourSetting {
    guidedTourKey: string;
    guidedTourStep: number;
    guidedTourState: GuidedTourState;

    constructor(guidedTourKey: string, guidedTourStep: number, guidedTourState?: GuidedTourState) {
        this.guidedTourKey = guidedTourKey;
        this.guidedTourStep = guidedTourStep;
        this.guidedTourState = guidedTourState ? guidedTourState : GuidedTourState.STARTED;
    }
}

/**
 * Guided tour mapping model which handles the mapping of tutorial to course and exercise
 */
export class GuidedTourMapping {
    courseShortName: string;
    tours: {
        [key: string]: string;
    };
}

import { GuidedTourState } from 'app/guided-tour/guided-tour.constants';

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

/**
 * Guided tour settings model which can dynamically handle the existing guided tours
 */
export class GuidedTourSetting {
    guidedTourKey: string;
    guidedTourStep: number;

    constructor(guidedTourKey: string, guidedTourStep: number) {
        this.guidedTourKey = guidedTourKey;
        this.guidedTourStep = guidedTourStep;
    }
}

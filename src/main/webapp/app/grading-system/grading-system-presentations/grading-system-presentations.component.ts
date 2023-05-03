import { Component, Input, OnChanges } from '@angular/core';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { GradingScale } from 'app/entities/grading-scale.model';

export enum PresentationType {
    NONE = 'none',
    BASIC = 'basic',
    GRADED = 'graded',
}
export interface PresentationsConfig {
    presentationType: PresentationType;
    presentationsWeight?: number;
    presentationsNumber?: number;
}

@Component({
    selector: 'jhi-grading-system-presentations',
    templateUrl: './grading-system-presentations.component.html',
    styleUrls: ['./grading-system-presentations.component.scss'],
})
export class GradingSystemPresentationsComponent implements OnChanges {
    readonly NONE = PresentationType.NONE;
    readonly BASIC = PresentationType.BASIC;
    readonly GRADED = PresentationType.GRADED;

    readonly modePickerOptions: ModePickerOption<PresentationType>[] = [
        {
            value: PresentationType.NONE,
            labelKey: 'artemisApp.gradingSystem.presentationType.none',
            btnClass: 'btn-secondary',
        },
        {
            value: PresentationType.GRADED,
            labelKey: 'artemisApp.gradingSystem.presentationType.graded',
            btnClass: 'btn-secondary',
        },
    ];

    @Input()
    gradingScale: GradingScale;

    @Input()
    presentationsConfig: PresentationsConfig;

    ngOnChanges(): void {
        this.initializePresentationConfig();
    }

    private initializePresentationConfig() {
        if (this.isGradedPresentation()) {
            this.presentationsConfig.presentationType = PresentationType.GRADED;
        } else {
            this.presentationsConfig.presentationType = PresentationType.NONE;
        }
        this.presentationsConfig.presentationsNumber = this.gradingScale.presentationsNumber;
        this.presentationsConfig.presentationsWeight = this.gradingScale.presentationsWeight;
    }

    isBasicPresentation(): boolean {
        return (this.gradingScale.course?.presentationScore ?? 0) > 0;
    }

    isGradedPresentation(): boolean {
        let isGradedPresentation = true;
        isGradedPresentation &&= (this.gradingScale.presentationsWeight ?? -1) >= 0;
        isGradedPresentation &&= (this.gradingScale.presentationsNumber ?? -1) > 0;
        return isGradedPresentation;
    }

    /**
     * Hook to indicate that presentation mode changed
     * @param presentationType - Presentation mode
     */
    onPresentationTypeChange(presentationType: PresentationType) {
        this.presentationsConfig.presentationType = presentationType;
        if (presentationType === PresentationType.NONE) {
            this.updatePresentationsNumber(undefined);
            this.updatePresentationsWeight(undefined);
        }
        if (presentationType === PresentationType.GRADED) {
            this.updatePresentationsNumber(2); // default value
            this.updatePresentationsWeight(20); // default value
        }
    }

    /**
     * Update number of presentations
     * @param presentationsNumber
     */
    updatePresentationsNumber(presentationsNumber?: number) {
        this.presentationsConfig.presentationsNumber = (presentationsNumber ?? -1) > 0 ? presentationsNumber : undefined;
        this.gradingScale.presentationsNumber = this.presentationsConfig.presentationsNumber;
    }

    /**
     * Update combined weight of presentations
     * @param presentationsWeight
     */
    updatePresentationsWeight(presentationsWeight?: number) {
        this.presentationsConfig.presentationsWeight = (presentationsWeight ?? -1) >= 0 ? presentationsWeight : undefined;
        this.gradingScale.presentationsWeight = this.presentationsConfig.presentationsWeight;
    }
}

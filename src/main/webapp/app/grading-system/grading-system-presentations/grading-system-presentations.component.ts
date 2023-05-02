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
    basicPresentationsNumber?: number;
    gradedPresentationsWeight?: number;
    gradedPresentationsNumber?: number;
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
            value: PresentationType.BASIC,
            labelKey: 'artemisApp.gradingSystem.presentationType.basic',
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
        } else if (this.isBasicPresentation()) {
            this.presentationsConfig.presentationType = PresentationType.BASIC;
        } else {
            this.presentationsConfig.presentationType = PresentationType.NONE;
        }
        this.presentationsConfig.gradedPresentationsNumber = this.gradingScale.presentationsNumber;
        this.presentationsConfig.gradedPresentationsWeight = this.gradingScale.presentationsWeight;
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
        switch (presentationType) {
            case PresentationType.NONE: {
                this.updateBasicPresentationsNumber(undefined);
                this.updateGradedPresentationsNumber(undefined);
                this.updateGradedPresentationsWeight(undefined);
                break;
            }
            case PresentationType.BASIC: {
                this.updateBasicPresentationsNumber(2); // default value
                this.updateGradedPresentationsNumber(undefined);
                this.updateGradedPresentationsWeight(undefined);
                break;
            }
            case PresentationType.GRADED: {
                this.updateBasicPresentationsNumber(undefined);
                this.updateGradedPresentationsNumber(2); // default value
                this.updateGradedPresentationsWeight(20); // default value
                break;
            }
        }
    }

    /**
     * Update number of basic presentations
     * @param presentationsNumber
     */
    updateBasicPresentationsNumber(presentationsNumber?: number) {
        if (this.gradingScale.course) {
            this.presentationsConfig.basicPresentationsNumber = (presentationsNumber ?? -1) > 0 ? presentationsNumber : undefined;
            this.gradingScale.course.presentationScore = this.presentationsConfig.basicPresentationsNumber;
        }
    }

    /**
     * Update number of graded presentations
     * @param presentationsNumber
     */
    updateGradedPresentationsNumber(presentationsNumber?: number) {
        this.presentationsConfig.gradedPresentationsNumber = (presentationsNumber ?? -1) > 0 ? presentationsNumber : undefined;
        this.gradingScale.presentationsNumber = this.presentationsConfig.gradedPresentationsNumber;
    }

    /**
     * Update combined weight of graded presentations
     * @param presentationsWeight
     */
    updateGradedPresentationsWeight(presentationsWeight?: number) {
        this.presentationsConfig.gradedPresentationsWeight = (presentationsWeight ?? -1) >= 0 ? presentationsWeight : undefined;
        this.gradingScale.presentationsWeight = this.presentationsConfig.gradedPresentationsWeight;
    }
}

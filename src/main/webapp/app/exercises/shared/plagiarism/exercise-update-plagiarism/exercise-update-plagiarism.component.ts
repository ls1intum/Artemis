import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild, model } from '@angular/core';
import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { NgModel } from '@angular/forms';
import { FormsModule } from 'app/forms/forms.module';

import { Subject, Subscription } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-exercise-update-plagiarism',
    templateUrl: './exercise-update-plagiarism.component.html',
    standalone: true,
    imports: [TranslateDirective, FaIconComponent, NgbTooltip, FormsModule, ArtemisSharedCommonModule],
})
export class ExerciseUpdatePlagiarismComponent implements OnInit, OnDestroy, AfterViewInit {
    exercise = model.required<Exercise>();
    @ViewChild('continuous_plagiarism_control_enabled') fieldCPCEnabled?: NgModel;
    @ViewChild('exercise.plagiarismDetectionConfig!.similarityThreshol') fieldThreshhold?: NgModel;
    @ViewChild('exercise.plagiarismDetectionConfig.minimumScore') fieldMinScore?: NgModel;
    @ViewChild('exercise.plagiarismDetectionConfig.minimumSize') fieldMinSize?: NgModel;
    @ViewChild('exercise.plagiarismDetectionConfig!.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod') fieldResponsePeriod?: NgModel;
    fieldCPCEnabledSubscription?: Subscription;
    fieldTreshholdSubscription?: Subscription;
    fieldMinScoreSubscription?: Subscription;
    fieldMinSizeSubscription?: Subscription;
    fieldResponsePeriodSubscription?: Subscription;

    isCPCCollapsed = true;

    minimumSizeTooltip?: string;
    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    readonly faQuestionCircle = faQuestionCircle;

    ngOnInit(): void {
        this.minimumSizeTooltip = this.getMinimumSizeTooltip();
        const exercise = this.exercise();
        if (exercise && !exercise?.plagiarismDetectionConfig) {
            // Create the default plagiarism configuration if there is none (e.g. importing an old exercise from a file)
            exercise.plagiarismDetectionConfig = DEFAULT_PLAGIARISM_DETECTION_CONFIG;
        }
    }

    ngAfterViewInit(): void {
        this.fieldCPCEnabledSubscription = this.fieldCPCEnabled?.valueChanges?.subscribe(() => this.calculateFormValid());
        this.fieldTreshholdSubscription = this.fieldThreshhold?.valueChanges?.subscribe(() => this.calculateFormValid());
        this.fieldMinScoreSubscription = this.fieldMinScore?.valueChanges?.subscribe(() => this.calculateFormValid());
        this.fieldMinSizeSubscription = this.fieldMinSize?.valueChanges?.subscribe(() => this.calculateFormValid());
        this.fieldResponsePeriodSubscription = this.fieldResponsePeriod?.valueChanges?.subscribe(() => this.calculateFormValid());
    }

    ngOnDestroy() {
        this.fieldCPCEnabledSubscription?.unsubscribe();
        this.fieldTreshholdSubscription?.unsubscribe();
        this.fieldMinScoreSubscription?.unsubscribe();
        this.fieldMinSizeSubscription?.unsubscribe();
        this.fieldResponsePeriodSubscription?.unsubscribe();
    }

    calculateFormValid(): void {
        this.formValid = Boolean(
            !this.exercise()?.plagiarismDetectionConfig?.continuousPlagiarismControlEnabled ||
                (this.fieldThreshhold?.valid && this.fieldMinScore?.valid && this.fieldMinSize?.valid && this.fieldResponsePeriod?.valid),
        );
        this.formValidChanges.next(this.formValid);
    }

    toggleCPCEnabled() {
        const config = this.exercise()?.plagiarismDetectionConfig!;
        const newValue = !config.continuousPlagiarismControlEnabled;
        config.continuousPlagiarismControlEnabled = newValue;
        config.continuousPlagiarismControlPostDueDateChecksEnabled = newValue;
    }

    /**
     * Return the translation identifier of the minimum size tooltip for the current exercise type.
     */
    getMinimumSizeTooltip(): string | undefined {
        switch (this.exercise()?.type) {
            case ExerciseType.PROGRAMMING: {
                return 'artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise';
            }
            case ExerciseType.TEXT: {
                return 'artemisApp.plagiarism.minimumSizeTooltipTextExercise';
            }
            case ExerciseType.MODELING: {
                return 'artemisApp.plagiarism.minimumSizeTooltipModelingExercise';
            }
        }
    }

    /**
     * updates the similaritythreshhold value of this exercise
     * @param threshHold the new threshold
     */
    updateSimilarityThreshold(threshHold: number) {
        this.exercise.update((exercise) => {
            if (exercise.plagiarismDetectionConfig) {
                exercise.plagiarismDetectionConfig.similarityThreshold = threshHold;
            }
            return exercise;
        });
    }

    /**
     * Updates the minimumScore value of this exercise
     * @param minimumScore The new minimum score
     */
    updateMinimumScore(minimumScore: number) {
        this.exercise.update((exercise) => {
            if (exercise.plagiarismDetectionConfig) {
                exercise.plagiarismDetectionConfig.minimumScore = minimumScore;
            }
            return exercise;
        });
    }

    /**
     * Updates the minimumSize value of this exercise
     * @param minimumSize The new minimum size
     */
    updateMinimumSize(minimumSize: number) {
        this.exercise.update((exercise) => {
            if (exercise.plagiarismDetectionConfig) {
                exercise.plagiarismDetectionConfig.minimumSize = minimumSize;
            }
            return exercise;
        });
    }

    /**
     * Updates the plagiarismCaseResponsePeriod value of this exercise
     * @param responsePeriod The new plagiarism case response period
     */
    updatePlagiarismCaseResponsePeriod(responsePeriod: number) {
        this.exercise.update((exercise) => {
            if (exercise.plagiarismDetectionConfig) {
                exercise.plagiarismDetectionConfig.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod = responsePeriod;
            }
            return exercise;
        });
    }

    /**
     * Updates the continuousPlagiarismControlEnabled value of this exercise
     * @param enabled Whether continuous plagiarism control is enabled
     */
    updateContinuousPlagiarismControlEnabled(enabled: boolean) {
        this.exercise.update((exercise) => {
            if (exercise.plagiarismDetectionConfig) {
                exercise.plagiarismDetectionConfig.continuousPlagiarismControlEnabled = enabled;
            }
            return exercise;
        });
    }

    /**
     * Updates the continuousPlagiarismControlPostDueDateChecksEnabled value of this exercise
     * @param enabled Whether post-due-date checks for continuous plagiarism control are enabled
     */
    updateContinuousPlagiarismControlPostDueDateChecksEnabled(enabled: boolean) {
        this.exercise.update((exercise) => {
            if (exercise.plagiarismDetectionConfig) {
                exercise.plagiarismDetectionConfig.continuousPlagiarismControlPostDueDateChecksEnabled = enabled;
            }
            return exercise;
        });
    }
}

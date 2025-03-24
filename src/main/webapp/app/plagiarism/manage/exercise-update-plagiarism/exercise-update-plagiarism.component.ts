import { AfterViewInit, Component, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FormsModule, NgModel } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exercise-update-plagiarism',
    templateUrl: './exercise-update-plagiarism.component.html',
    imports: [TranslateDirective, FaIconComponent, NgbTooltip, FormsModule, ArtemisTranslatePipe],
})
export class ExerciseUpdatePlagiarismComponent implements OnInit, OnDestroy, AfterViewInit {
    @Input() exercise: Exercise;
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
        if (!this.exercise.plagiarismDetectionConfig) {
            // Create the default plagiarism configuration if there is none (e.g. importing an old exercise from a file)
            this.exercise.plagiarismDetectionConfig = DEFAULT_PLAGIARISM_DETECTION_CONFIG;
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
            !this.exercise.plagiarismDetectionConfig?.continuousPlagiarismControlEnabled ||
                (this.fieldThreshhold?.valid && this.fieldMinScore?.valid && this.fieldMinSize?.valid && this.fieldResponsePeriod?.valid),
        );
        this.formValidChanges.next(this.formValid);
    }

    toggleCPCEnabled() {
        const config = this.exercise.plagiarismDetectionConfig!;
        const newValue = !config.continuousPlagiarismControlEnabled;
        config.continuousPlagiarismControlEnabled = newValue;
        config.continuousPlagiarismControlPostDueDateChecksEnabled = newValue;
    }

    /**
     * Return the translation identifier of the minimum size tooltip for the current exercise type.
     */
    getMinimumSizeTooltip(): string | undefined {
        switch (this.exercise.type) {
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
}

import { Component, OnDestroy, OnInit, Signal, effect, inject, model, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Subscription, tap } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { toSignal } from '@angular/core/rxjs-interop';
@Component({
    selector: 'jhi-exercise-update-plagiarism',
    templateUrl: './exercise-update-plagiarism.component.html',
    standalone: true,
    imports: [ReactiveFormsModule, TranslateDirective, FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class ExerciseUpdatePlagiarismComponent implements OnInit, OnDestroy {
    readonly exercise = model.required<Exercise>();
    readonly fb = inject(FormBuilder);

    form: FormGroup;

    private formStatus: Signal<string>;
    private formSubscription: Subscription;

    isCPCCollapsed = true;
    readonly faQuestionCircle = faQuestionCircle;
    isFormValid = signal(false);

    constructor() {
        this.form = this.fb.group({
            continuousPlagiarismControlEnabled: [DEFAULT_PLAGIARISM_DETECTION_CONFIG.continuousPlagiarismControlEnabled],
            continuousPlagiarismControlPostDueDateChecksEnabled: [DEFAULT_PLAGIARISM_DETECTION_CONFIG.continuousPlagiarismControlPostDueDateChecksEnabled],
            similarityThreshold: [DEFAULT_PLAGIARISM_DETECTION_CONFIG.similarityThreshold, [Validators.required, Validators.min(0), Validators.max(100)]],
            minimumScore: [DEFAULT_PLAGIARISM_DETECTION_CONFIG.minimumScore, [Validators.required, Validators.min(0), Validators.max(100)]],
            minimumSize: [DEFAULT_PLAGIARISM_DETECTION_CONFIG.minimumSize, [Validators.required, Validators.min(0)]],
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: [
                DEFAULT_PLAGIARISM_DETECTION_CONFIG.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod,
                [Validators.required, Validators.min(7), Validators.max(31)],
            ],
        });

        this.formStatus = toSignal(this.form.statusChanges, { initialValue: this.form.status });

        effect(() => this.isFormValid.set(this.formStatus() === 'VALID'));

        this.formSubscription = this.form.valueChanges
            .pipe(
                tap((form) => {
                    const enabled = form.continuousPlagiarismControlEnabled;
                    [
                        'continuousPlagiarismControlPostDueDateChecksEnabled',
                        'similarityThreshold',
                        'minimumScore',
                        'minimumSize',
                        'continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod',
                    ].forEach((k) => this.form.get(k)?.[enabled ? 'enable' : 'disable']({ emitEvent: false }));
                }),
            )
            .subscribe(() => {
                this.exercise.update((exercise) => {
                    // Use getRawValue() to include disabled fields (they are excluded from valueChanges)
                    exercise.plagiarismDetectionConfig = { ...this.form.getRawValue() };
                    return exercise;
                });
            });
    }

    ngOnInit(): void {
        if (this.exercise() && !this.exercise()?.plagiarismDetectionConfig) {
            this.exercise().plagiarismDetectionConfig = DEFAULT_PLAGIARISM_DETECTION_CONFIG;
        }

        this.form.patchValue({
            continuousPlagiarismControlEnabled:
                this.exercise()?.plagiarismDetectionConfig?.continuousPlagiarismControlEnabled ?? DEFAULT_PLAGIARISM_DETECTION_CONFIG.continuousPlagiarismControlEnabled,
            continuousPlagiarismControlPostDueDateChecksEnabled:
                this.exercise()?.plagiarismDetectionConfig?.continuousPlagiarismControlPostDueDateChecksEnabled ??
                DEFAULT_PLAGIARISM_DETECTION_CONFIG.continuousPlagiarismControlPostDueDateChecksEnabled,
            similarityThreshold: this.exercise()?.plagiarismDetectionConfig?.similarityThreshold ?? DEFAULT_PLAGIARISM_DETECTION_CONFIG.similarityThreshold,
            minimumScore: this.exercise()?.plagiarismDetectionConfig?.minimumScore ?? DEFAULT_PLAGIARISM_DETECTION_CONFIG.minimumScore,
            minimumSize: this.exercise()?.plagiarismDetectionConfig?.minimumSize ?? DEFAULT_PLAGIARISM_DETECTION_CONFIG.minimumSize,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod:
                this.exercise()?.plagiarismDetectionConfig?.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod ??
                DEFAULT_PLAGIARISM_DETECTION_CONFIG.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod,
        });
    }

    getMinimumSizeLabel(): string {
        switch (this.exercise()?.type) {
            case ExerciseType.PROGRAMMING: {
                return 'artemisApp.plagiarism.minimumTokenCount';
            }
            case ExerciseType.TEXT: {
                return 'artemisApp.plagiarism.minimumSize';
            }
            default: {
                return '';
            }
        }
    }

    /**
     * Return the translation identifier of the minimum size tooltip for the current exercise type.
     */
    getMinimumSizeTooltip(): string {
        switch (this.exercise()?.type) {
            case ExerciseType.PROGRAMMING:
                return 'artemisApp.plagiarism.minimumTokenCountTooltipProgrammingExercise';
            case ExerciseType.TEXT:
                return 'artemisApp.plagiarism.minimumSizeTooltipTextExercise';
            default:
                return '';
        }
    }

    ngOnDestroy(): void {
        this.formSubscription.unsubscribe();
    }
}

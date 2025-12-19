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
    styleUrls: ['./exercise-update-plagiarism.component.scss'],
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
            continuousPlagiarismControlEnabled: [false],
            continuousPlagiarismControlPostDueDateChecksEnabled: [false],
            similarityThreshold: [null, [Validators.required, Validators.min(0), Validators.max(100)]],
            minimumScore: [null, [Validators.required, Validators.min(0), Validators.max(100)]],
            minimumSize: [null, [Validators.required, Validators.min(0), Validators.max(100)]],
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: [null, [Validators.required, Validators.min(7), Validators.max(31)]],
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
            .subscribe((plagiarismDetectionConfig) => {
                this.exercise.update((exercise) => {
                    exercise.plagiarismDetectionConfig = { ...plagiarismDetectionConfig };
                    return exercise;
                });
            });
    }

    ngOnInit(): void {
        if (this.exercise() && !this.exercise()?.plagiarismDetectionConfig) {
            this.exercise().plagiarismDetectionConfig = DEFAULT_PLAGIARISM_DETECTION_CONFIG;
        }

        this.form.patchValue({
            continuousPlagiarismControlEnabled: this.exercise()?.plagiarismDetectionConfig?.continuousPlagiarismControlEnabled ?? false,
            continuousPlagiarismControlPostDueDateChecksEnabled: this.exercise()?.plagiarismDetectionConfig?.continuousPlagiarismControlPostDueDateChecksEnabled ?? false,
            similarityThreshold: this.exercise()?.plagiarismDetectionConfig?.similarityThreshold ?? null,
            minimumScore: this.exercise()?.plagiarismDetectionConfig?.minimumScore ?? null,
            minimumSize: this.exercise()?.plagiarismDetectionConfig?.minimumSize ?? null,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod:
                this.exercise()?.plagiarismDetectionConfig?.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod ?? null,
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

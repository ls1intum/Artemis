import { Component, OnInit, Signal, computed, effect, input } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
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
export class ExerciseUpdatePlagiarismComponent implements OnInit {
    readonly exercise = input.required<Exercise>();

    form: FormGroup;

    private formStatus: Signal<string>;

    isFormValid = computed(() => this.formStatus() === 'VALID');

    formValid: boolean;
    formValidChanges = new Subject<boolean>();
    isCPCCollapsed = true;
    minimumSizeTooltip?: string;
    readonly faQuestionCircle = faQuestionCircle;

    constructor(private fb: FormBuilder) {
        this.form = this.fb.group({
            continuousPlagiarismControlEnabled: [false],
            continuousPlagiarismControlPostDueDateChecksEnabled: [false],
            similarityThreshold: [null, [Validators.required, Validators.min(0), Validators.max(100)]],
            minimumScore: [null, [Validators.required, Validators.min(0), Validators.max(100)]],
            minimumSize: [null, [Validators.required, Validators.min(0), Validators.max(100)]],
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: [null, [Validators.required, Validators.min(7), Validators.max(31)]],
        });

        this.formStatus = toSignal(this.form.statusChanges, { initialValue: this.form.status });

        this.isFormValid = computed(() => this.formStatus() === 'VALID');

        this.form.get('continuousPlagiarismControlEnabled')!.valueChanges.subscribe((enabled: boolean) => {
            const fields = [
                'continuousPlagiarismControlPostDueDateChecksEnabled',
                'similarityThreshold',
                'minimumScore',
                'minimumSize',
                'continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod',
            ];
            fields.forEach((key) => {
                const ctrl = this.form.get(key);
                if (enabled) {
                    ctrl?.enable();
                } else {
                    ctrl?.disable();
                }
            });
        });

        effect(() => {
            this.formValid = this.isFormValid();
            this.formValidChanges.next(this.formValid);
        });
    }

    ngOnInit(): void {
        const ex = this.exercise();
        if (ex && !ex.plagiarismDetectionConfig) {
            ex.plagiarismDetectionConfig = DEFAULT_PLAGIARISM_DETECTION_CONFIG;
        }
        this.minimumSizeTooltip = this.getMinimumSizeTooltip();

        this.form.patchValue({
            continuousPlagiarismControlEnabled: ex?.plagiarismDetectionConfig?.continuousPlagiarismControlEnabled ?? false,
            continuousPlagiarismControlPostDueDateChecksEnabled: ex?.plagiarismDetectionConfig?.continuousPlagiarismControlPostDueDateChecksEnabled ?? false,
            similarityThreshold: ex?.plagiarismDetectionConfig?.similarityThreshold ?? null,
            minimumScore: ex?.plagiarismDetectionConfig?.minimumScore ?? null,
            minimumSize: ex?.plagiarismDetectionConfig?.minimumSize ?? null,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: ex?.plagiarismDetectionConfig?.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod ?? null,
        });
    }

    /**
     * Return the translation identifier of the minimum size tooltip for the current exercise type.
     */
    getMinimumSizeTooltip(): string | undefined {
        switch (this.exercise()?.type) {
            case ExerciseType.PROGRAMMING:
                return 'artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise';
            case ExerciseType.TEXT:
                return 'artemisApp.plagiarism.minimumSizeTooltipTextExercise';
        }
    }
}

import { ChangeDetectorRef, Component, OnChanges, OnInit, SimpleChanges, input } from '@angular/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Observable } from 'rxjs';
import { AthenaService } from 'app/assessment/athena.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { AsyncPipe, NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'jhi-exercise-preliminary-feedback-options',
    imports: [TranslateDirective, NgStyle, HelpIconComponent, FormsModule, AsyncPipe],
    templateUrl: './exercise-preliminary-feedback-options.component.html',
})
export class ExercisePreliminaryFeedbackOptionsComponent implements OnInit, OnChanges {
    exercise = input.required<Exercise>();
    dueDate = input<dayjs.Dayjs>();
    readOnly = input<boolean>();

    protected readonly ExerciseType = ExerciseType;

    protected readonly AssessmentType = AssessmentType;

    readonly assessmentType: AssessmentType;

    isAthenaEnabled$: Observable<boolean>;
    modulesAvailable: boolean;
    availableAthenaModules: string[];
    initialAthenaModule?: string;
    showDropdownList: boolean = false;

    constructor(
        private athenaService: AthenaService,
        private activatedRoute: ActivatedRoute,
        private cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        const courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.athenaService.getAvailableModules(courseId, this.exercise()).subscribe((modules) => {
            this.availableAthenaModules = modules;
            this.modulesAvailable = modules.length > 0;
            this.cdr.detectChanges();
        });
        this.isAthenaEnabled$ = this.athenaService.isEnabled();
        this.initialAthenaModule = this.exercise().preliminaryFeedbackModule;
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.dueDate && !changes.dueDate.isFirstChange()) {
            if (this.inputControlsDisabled()) {
                this.exercise().preliminaryFeedbackModule = this.initialAthenaModule;
            }
        }
    }

    /**
     * Returns true in case the input controls should be disabled. This is the case for all exercises when the due date has passed.
     */
    inputControlsDisabled() {
        return this.readOnly() || this.hasDueDatePassed();
    }

    /**
     * Returns the label style for the checkbox to enable preliminary feedback. In case the input controls are disabled, the label text color is set to grey.
     */
    getCheckboxLabelStyle() {
        if (this.inputControlsDisabled()) {
            return { color: 'grey' };
        }
        return {};
    }

    togglePreliminaryFeedback(event: any) {
        this.showDropdownList = event.target.checked;
        if (event.target.checked) {
            this.exercise().preliminaryFeedbackModule = this.availableAthenaModules.first();
        } else {
            this.exercise().preliminaryFeedbackModule = undefined;
        }
    }

    private hasDueDatePassed() {
        return dayjs(this.exercise().dueDate).isBefore(dayjs());
    }
}

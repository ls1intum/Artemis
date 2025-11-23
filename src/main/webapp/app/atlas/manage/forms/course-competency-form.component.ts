import { Component, InputSignal, inject, input, output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { of } from 'rxjs';
import { catchError, delay, map, switchMap } from 'rxjs/operators';
import { CompetencyTaxonomy, DEFAULT_MASTERY_THRESHOLD } from 'app/atlas/shared/entities/competency.model';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';

/**
 * Async Validator to make sure that a competency title is unique within a course
 */
export const titleUniqueValidator = (courseCompetencyService: CourseCompetencyService, courseId: number, initialTitle?: string) => {
    return (competencyTitleControl: FormControl<string | undefined>) => {
        return of(competencyTitleControl.value).pipe(
            delay(250),
            switchMap((title) => {
                if (initialTitle && title === initialTitle) {
                    return of(null);
                }
                return courseCompetencyService.getCourseCompetencyTitles(courseId).pipe(
                    map((res) => {
                        const competencyTitles = res.body!;
                        if (title && competencyTitles.includes(title)) {
                            return {
                                titleUnique: { valid: false },
                            };
                        } else {
                            return null;
                        }
                    }),
                    catchError(() => of(null)),
                );
            }),
        );
    };
};

export interface CourseCompetencyFormData {
    id?: number;
    title?: string;
    description?: string;
    softDueDate?: dayjs.Dayjs;
    taxonomy?: CompetencyTaxonomy;
    optional?: boolean;
    masteryThreshold?: number;
}

@Component({
    template: '',
})
export abstract class CourseCompetencyFormComponent {
    abstract formData: InputSignal<CourseCompetencyFormData>;

    private fb = inject(FormBuilder);
    private courseCompetencyService = inject(CourseCompetencyService);

    isEditMode = input<boolean>(false);
    isInConnectMode = input<boolean>(false);
    isInSingleLectureMode = input<boolean>(false);
    courseId = input<number>(0);
    averageStudentScore = input<number>();
    hasCancelButton = input<boolean>(false);

    onCancel = output<void>();
    formSubmitted = output<CourseCompetencyFormData>();

    form: FormGroup;

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faQuestionCircle = faQuestionCircle;

    get titleControl() {
        return this.form.get('title');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get softDueDateControl() {
        return this.form.get('softDueDate');
    }

    get optionalControl() {
        return this.form.get('optional');
    }

    /**
     * Updates description form on markdown change
     * @param content markdown content
     */
    updateDescriptionControl(content: string) {
        this.descriptionControl?.setValue(content);
        this.descriptionControl?.markAsDirty();
    }

    protected initializeForm() {
        if (this.form) {
            return;
        }
        let initialTitle: string | undefined = undefined;
        if (this.isEditMode() && this.formData() && this.formData().title) {
            initialTitle = this.formData().title;
        }
        this.form = this.fb.nonNullable.group({
            title: [
                undefined as string | undefined,
                [Validators.required, Validators.maxLength(255)],
                [titleUniqueValidator(this.courseCompetencyService, this.courseId(), initialTitle)],
            ],
            description: [undefined as string | undefined, [Validators.maxLength(10000)]],
            softDueDate: [undefined],
            taxonomy: [undefined as CompetencyTaxonomy | undefined],
            masteryThreshold: [DEFAULT_MASTERY_THRESHOLD, [Validators.min(0), Validators.max(100)]],
            optional: [false],
        });
    }

    cancelForm() {
        this.onCancel.emit();
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }
}

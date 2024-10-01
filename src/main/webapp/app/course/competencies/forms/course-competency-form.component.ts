import { Component, EventEmitter, Output, input } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { of } from 'rxjs';
import { catchError, delay, map, switchMap } from 'rxjs/operators';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { CompetencyTaxonomy, DEFAULT_MASTERY_THRESHOLD } from 'app/entities/competency.model';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';
import { TranslateService } from '@ngx-translate/core';

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
    connectedLectureUnits?: LectureUnit[];
}

@Component({ template: '' })
export abstract class CourseCompetencyFormComponent {
    abstract formData: CourseCompetencyFormData;

    isEditMode = input<boolean>(false);
    isInConnectMode = input<boolean>(false);
    isInSingleLectureMode = input<boolean>(false);
    hasLinkedStandardizedCompetency = input<boolean>(false);
    courseId = input.required<number>();
    lecturesOfCourseWithLectureUnits = input<Lecture[]>([]);
    averageStudentScore = input<number>();
    hasCancelButton = input<boolean>(true);

    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();
    @Output()
    formSubmitted: EventEmitter<CourseCompetencyFormData> = new EventEmitter<CourseCompetencyFormData>();

    form: FormGroup;
    selectedLectureUnitsInTable: LectureUnit[] = [];

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faQuestionCircle = faQuestionCircle;

    // Constants
    protected readonly competencyTaxonomy = CompetencyTaxonomy;

    protected constructor(
        protected fb: FormBuilder,
        protected lectureUnitService: LectureUnitService,
        protected courseCompetencyService: CourseCompetencyService,
        protected translateService: TranslateService,
    ) {}

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
        if (this.isEditMode() && this.formData && this.formData.title) {
            initialTitle = this.formData.title;
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
        this.selectedLectureUnitsInTable = [];
    }

    cancelForm() {
        this.onCancel.emit();
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    protected onLectureUnitSelectionChange(lectureUnits: LectureUnit[]) {
        this.selectedLectureUnitsInTable = lectureUnits;
    }
}

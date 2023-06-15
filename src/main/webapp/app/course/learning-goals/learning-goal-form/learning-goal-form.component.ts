import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { merge, of } from 'rxjs';
import { catchError, delay, map, switchMap } from 'rxjs/operators';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { TranslateService } from '@ngx-translate/core';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { intersection } from 'lodash-es';
import { LearningGoalTaxonomy } from 'app/entities/learningGoal.model';
import { faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { ValidatorFn } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';

/**
 * Async Validator to make sure that a competency title is unique within a course
 */
export const titleUniqueValidator = (learningGoalService: LearningGoalService, courseId: number, initialTitle?: string) => {
    return (learningGoalTitleControl: FormControl<string | undefined>) => {
        return of(learningGoalTitleControl.value).pipe(
            delay(250),
            switchMap((title) => {
                if (initialTitle && title === initialTitle) {
                    return of(null);
                }
                return learningGoalService.getAllForCourse(courseId).pipe(
                    map((res) => {
                        let learningGoalTitles: string[] = [];
                        if (res.body) {
                            learningGoalTitles = res.body.map((learningGoal) => learningGoal.title!);
                        }
                        if (title && learningGoalTitles.includes(title)) {
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

export interface LearningGoalFormData {
    id?: number;
    title?: string;
    description?: string;
    taxonomy?: LearningGoalTaxonomy;
    optional?: boolean;
    masteryThreshold?: number;
    connectedLectureUnits?: LectureUnit[];
}

@Component({
    selector: 'jhi-learning-goal-form',
    templateUrl: './learning-goal-form.component.html',
    styleUrls: ['./learning-goal-form.component.scss'],
})
export class LearningGoalFormComponent implements OnInit, OnChanges {
    @Input()
    formData: LearningGoalFormData = {
        id: undefined,
        title: undefined,
        description: undefined,
        taxonomy: undefined,
        masteryThreshold: undefined,
        optional: false,
        connectedLectureUnits: undefined,
    };

    @Input()
    isEditMode = false;
    @Input()
    isInConnectMode = false;
    @Input()
    isInSingleLectureMode = false;
    @Input()
    courseId: number;
    @Input()
    lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Input()
    averageStudentScore?: number;
    @Input()
    hasCancelButton: boolean;
    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();

    titleUniqueValidator = titleUniqueValidator;
    learningGoalTaxonomy = LearningGoalTaxonomy;

    @Output()
    formSubmitted: EventEmitter<LearningGoalFormData> = new EventEmitter<LearningGoalFormData>();

    form: FormGroup;
    selectedLectureInDropdown: Lecture;
    selectedLectureUnitsInTable: LectureUnit[] = [];
    suggestedTaxonomies: string[] = [];
    canBeOptional = false;

    faTimes = faTimes;
    faQuestionCircle = faQuestionCircle;

    constructor(
        private fb: FormBuilder,
        private learningGoalService: LearningGoalService,
        private translateService: TranslateService,
        public lectureUnitService: LectureUnitService,
        private alertService: AlertService,
    ) {}

    get titleControl() {
        return this.form.get('title');
    }

    get descriptionControl() {
        return this.form.get('description');
    }

    get masteryThresholdControl() {
        return this.form.get('masteryThreshold');
    }

    get optionalControl() {
        return this.form.get('optional');
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    ngOnInit(): void {
        this.initializeForm();
    }

    private initializeForm() {
        this.canBeOptional = this.checkCanBeOptional();
        if (this.form) {
            return;
        }
        let initialTitle: string | undefined = undefined;
        if (this.isEditMode && this.formData && this.formData.title) {
            initialTitle = this.formData.title;
        }
        this.form = this.fb.group({
            title: [
                undefined as string | undefined,
                [Validators.required, Validators.maxLength(255)],
                [this.titleUniqueValidator(this.learningGoalService, this.courseId, initialTitle)],
            ],
            description: [undefined as string | undefined, [Validators.maxLength(10000)]],
            taxonomy: [undefined, [Validators.pattern('^(' + Object.keys(this.learningGoalTaxonomy).join('|') + ')$')]],
            masteryThreshold: [undefined, [Validators.min(0), Validators.max(100)]],
            optional: [false],
        });
        this.selectedLectureUnitsInTable = [];

        merge(this.titleControl!.valueChanges, this.descriptionControl!.valueChanges).subscribe(() => this.suggestTaxonomies());

        if (this.isInSingleLectureMode) {
            this.selectLectureInDropdown(this.lecturesOfCourseWithLectureUnits.first()!);
        }
    }

    private setFormValues(formData: LearningGoalFormData) {
        this.form.patchValue(formData);
        if (formData.connectedLectureUnits) {
            this.selectedLectureUnitsInTable = formData.connectedLectureUnits;
        }
    }

    cancelForm() {
        this.onCancel.emit();
    }

    submitForm() {
        const learningGoalFormData: LearningGoalFormData = { ...this.form.value };
        learningGoalFormData.connectedLectureUnits = this.selectedLectureUnitsInTable;
        this.formSubmitted.emit(learningGoalFormData);
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    selectLectureInDropdown(lecture: Lecture) {
        this.selectedLectureInDropdown = lecture;
    }

    /**
     * Needed to keep the order in keyvalue pipe
     */
    keepOrder = () => {
        return 0;
    };

    /**
     * Suggest some taxonomies based on keywords used in the title or description.
     * Triggered after the user changes the title or description input field.
     */
    suggestTaxonomies() {
        this.suggestedTaxonomies = [];
        const title = this.titleControl?.value?.toLowerCase() ?? '';
        const description = this.descriptionControl?.value?.toLowerCase() ?? '';
        for (const taxonomy in this.learningGoalTaxonomy) {
            const keywords = this.translateService.instant('artemisApp.learningGoal.keywords.' + taxonomy.toLowerCase()).split(', ');
            const taxonomyName = this.translateService.instant('artemisApp.learningGoal.taxonomies.' + taxonomy.toLowerCase());
            keywords.push(taxonomyName);
            if (keywords.map((keyword: string) => keyword.toLowerCase()).some((keyword: string) => title.includes(keyword) || description.includes(keyword))) {
                this.suggestedTaxonomies.push(taxonomyName);
            }
        }
    }

    selectLectureUnitInTable(lectureUnit: LectureUnit) {
        if (this.isLectureUnitAlreadySelectedInTable(lectureUnit)) {
            this.selectedLectureUnitsInTable.forEach((selectedLectureUnit, index) => {
                if (selectedLectureUnit.id === lectureUnit.id) {
                    this.selectedLectureUnitsInTable.splice(index, 1);
                }
            });
        } else {
            this.selectedLectureUnitsInTable.push(lectureUnit);
        }
    }

    isLectureUnitAlreadySelectedInTable(lectureUnit: LectureUnit) {
        return this.selectedLectureUnitsInTable.map((selectedLectureUnit) => selectedLectureUnit.id).includes(lectureUnit.id);
    }

    getLectureTitleForDropdown(lecture: Lecture) {
        const noOfSelectedUnitsInLecture = intersection(
            this.selectedLectureUnitsInTable.map((unit) => unit.id),
            lecture.lectureUnits?.map((unit) => unit.id),
        ).length;
        return this.translateService.instant('artemisApp.learningGoal.createLearningGoal.dropdown', {
            lectureTitle: lecture.title,
            noOfConnectedUnits: noOfSelectedUnitsInLecture,
        });
    }

    private checkCanBeOptional(): boolean {
        if (!this.isEditMode || !this.formData.id) {
            return true;
        }

        let requiredExerciseExists = false;
        this.learningGoalService
            .findById(this.formData.id, this.courseId)
            .pipe(
                map((res) => {
                    if (res.body) {
                        return res.body;
                    }
                    return null;
                }),
            )
            .subscribe({
                next: (learningGoal) => {
                    if (learningGoal && learningGoal.exercises) {
                        requiredExerciseExists = learningGoal.exercises.some((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY);
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        return !requiredExerciseExists;
    }

    getCanBeOptional(): boolean {
        return this.canBeOptional;
    }

    /**
     * Validator to make sure that a competency can only be optional if no exercise is completely included in the score
     */
    canBeOptionalValidator(): ValidatorFn {
        return (control: FormControl) => {
            console.log('executed 1');

            if (!this.form?.value.optional) {
                console.log('executed 1.1 ' + this.form?.value.optional);
                return null;
            }

            console.log('executed 2');

            if (!this.formData.id) {
                return null;
            }

            let requiredExerciseExists = false;
            console.log('executed 3');
            this.learningGoalService
                .findById(this.formData.id, this.courseId)
                .pipe(
                    map((res) => {
                        if (res.body) {
                            return res.body;
                        }
                        return null;
                    }),
                )
                .subscribe({
                    next: (learningGoal) => {
                        if (learningGoal && learningGoal.exercises) {
                            requiredExerciseExists = learningGoal.exercises.some((exercise) => exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY);
                        }
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
            return requiredExerciseExists ? { canBeOptional: false } : null;
        };
    }
}

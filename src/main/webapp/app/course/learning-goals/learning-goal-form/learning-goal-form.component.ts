import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { catchError, delay, map, switchMap } from 'rxjs/operators';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { TranslateService } from '@ngx-translate/core';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { intersection } from 'lodash-es';
import { LearningGoalTaxonomy } from 'app/entities/learningGoal.model';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

/**
 * Async Validator to make sure that a learning goal title is unique within a course
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
        connectedLectureUnits: undefined,
    };

    @Input()
    isEditMode = false;
    @Input()
    courseId: number;
    @Input()
    lecturesOfCourseWithLectureUnits: Lecture[] = [];

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

    faTimes = faTimes;

    constructor(
        private fb: FormBuilder,
        private learningGoalService: LearningGoalService,
        private translateService: TranslateService,
        public lectureUnitService: LectureUnitService,
    ) {}

    get titleControl() {
        return this.form.get('title');
    }

    get descriptionControl() {
        return this.form.get('description');
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
        });
        this.selectedLectureUnitsInTable = [];
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
}

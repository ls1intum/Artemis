import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Competency, getIcon } from 'app/entities/competency.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ActivatedRoute } from '@angular/router';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { finalize } from 'rxjs';

@Component({
    selector: 'jhi-competency-selection',
    templateUrl: './competency-selection.component.html',
    styleUrls: ['./competency-selection.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: forwardRef(() => CompetencySelectionComponent),
        },
    ],
})
export class CompetencySelectionComponent implements OnInit, ControlValueAccessor {
    @Input() labelName: string;
    @Input() labelTooltip: string;
    // selected competencies
    @Input() value?: Competency[];
    @Input() disabled: boolean;
    // all course competencies
    @Input() competencies?: Competency[];

    @Output() valueChange = new EventEmitter();

    isLoading = false;
    checkboxStates: Record<number, boolean>;

    getIcon = getIcon;
    faQuestionCircle = faQuestionCircle;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (value: any) => {};

    constructor(
        private route: ActivatedRoute,
        private courseStorageService: CourseStorageService,
        private competencyService: CompetencyService,
        private changeDetector: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (!this.competencies && courseId) {
            const course = this.courseStorageService.getCourse(courseId);
            // an empty array is used as fallback, if a course is cached, where no competencies have been queried
            if (course?.competencies?.length) {
                this.setCompetencies(course.competencies!);
            } else {
                this.isLoading = true;
                this.competencyService
                    .getAllForCourse(courseId)
                    .pipe(
                        finalize(() => {
                            this.isLoading = false;

                            // trigger change detection manually
                            // necessary because quiz exercises use ChangeDetectionStrategy.OnPush
                            this.changeDetector.detectChanges();
                        }),
                    )
                    .subscribe({
                        next: (response) => {
                            this.setCompetencies(response.body!);
                            this.writeValue(this.value);
                        },
                        error: () => {
                            this.disabled = true;
                        },
                    });
            }
        }
    }

    /**
     * Set the available competencies for selection
     * @param competencies The competencies of the course
     */
    setCompetencies(competencies: Competency[]) {
        this.competencies = competencies.map((competency) => {
            // Remove unnecessary properties
            competency.course = undefined;
            competency.userProgress = undefined;
            return competency;
        });
        this.checkboxStates = this.competencies.reduce(
            (states, competency) => {
                if (competency.id) {
                    states[competency.id] = !!this.value?.find((value) => value.id === competency.id);
                }
                return states;
            },
            {} as Record<number, boolean>,
        );
    }

    toggleCompetency(newValue: Competency) {
        if (newValue.id) {
            if (this.checkboxStates[newValue.id]) {
                this.value = this.value?.filter((value) => value.id !== newValue.id);
            } else {
                this.value = [...(this.value ?? []), newValue];
            }

            this.checkboxStates[newValue.id] = !this.checkboxStates[newValue.id];

            // make sure to do not send an empty list to server
            if (!this.value?.length) {
                this.value = undefined;
            }

            this._onChange(this.value);
            this.valueChange.emit(this.value);
        }
    }

    writeValue(value?: Competency[]): void {
        if (value && this.competencies) {
            // Compare the ids of the competencies instead of the whole objects
            const ids = value.map((el) => el.id);
            this.value = this.competencies.filter((competency) => ids.includes(competency.id));
        } else {
            this.value = value ?? [];
        }
    }

    registerOnChange(fn: any): void {
        this._onChange = fn;
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    registerOnTouched(fn: any): void {}

    setDisabledState?(isDisabled: boolean): void {
        this.disabled = isDisabled;
    }
}

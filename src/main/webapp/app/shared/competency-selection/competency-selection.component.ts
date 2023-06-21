import { Component, EventEmitter, Input, OnInit, Output, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Competency, getIcon } from 'app/entities/competency.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ActivatedRoute } from '@angular/router';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Component({
    selector: 'jhi-competency-selection',
    templateUrl: './competency-selection.component.html',
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
    @Input() value: any;
    @Input() disabled: boolean;
    @Input() error: boolean;
    @Input() competencies: Competency[];

    @Output() valueChange = new EventEmitter();

    isLoading = false;

    getIcon = getIcon;
    faQuestionCircle = faQuestionCircle;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (value: any) => {};

    constructor(private route: ActivatedRoute, private courseStorageService: CourseStorageService, private competencyService: CompetencyService) {}

    ngOnInit(): void {
        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (this.competencies == undefined && courseId) {
            const course = this.courseStorageService.getCourse(courseId);
            if (course?.competencies) {
                this.setCompetencies(course.competencies!);
            } else {
                this.isLoading = true;
                this.competencyService.getAllForCourse(courseId).subscribe({
                    next: (response) => {
                        this.setCompetencies(response.body!);
                        this.writeValue(this.value);
                        this.isLoading = false;
                    },
                    error: () => {
                        this.disabled = true;
                        this.isLoading = false;
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
    }

    updateField(newValue: Competency[]) {
        this.value = newValue;
        this._onChange(this.value);
        this.valueChange.emit();
    }

    writeValue(value?: Competency[]): void {
        if (value && this.competencies) {
            // Compare the ids of the competencies instead of the whole objects
            const ids = value.map((el) => el.id);
            this.value = this.competencies.filter((competency) => ids.includes(competency.id));
        } else {
            this.value = value;
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

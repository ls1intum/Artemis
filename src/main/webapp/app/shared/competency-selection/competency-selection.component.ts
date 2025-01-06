import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import {
    CompetencyLearningObjectLink,
    CourseCompetency,
    HIGH_COMPETENCY_LINK_WEIGHT,
    LOW_COMPETENCY_LINK_WEIGHT,
    LOW_COMPETENCY_LINK_WEIGHT_CUT_OFF,
    MEDIUM_COMPETENCY_LINK_WEIGHT,
    MEDIUM_COMPETENCY_LINK_WEIGHT_CUT_OFF,
    getIcon,
} from 'app/entities/competency.model';
import { ActivatedRoute } from '@angular/router';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { finalize } from 'rxjs';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from '../language/translate.directive';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';

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
    imports: [FaStackComponent, NgbTooltip, FaIconComponent, FaStackItemSizeDirective, FormsModule, TranslateDirective, ArtemisTranslatePipe],
})
export class CompetencySelectionComponent implements OnInit, ControlValueAccessor {
    private route = inject(ActivatedRoute);
    private courseStorageService = inject(CourseStorageService);
    private courseCompetencyService = inject(CourseCompetencyService);
    private changeDetector = inject(ChangeDetectorRef);

    @Input() labelName: string;
    @Input() labelTooltip: string;

    @Output() valueChange = new EventEmitter();

    disabled: boolean;
    // selected competencies
    selectedCompetencyLinks?: CompetencyLearningObjectLink[];
    // all course competencies
    competencyLinks?: CompetencyLearningObjectLink[];

    isLoading = false;
    checkboxStates: Record<number, boolean>;

    getIcon = getIcon;
    faQuestionCircle = faQuestionCircle;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (value: any) => {};

    protected readonly HIGH_COMPETENCY_LINK_WEIGHT = HIGH_COMPETENCY_LINK_WEIGHT;
    protected readonly MEDIUM_COMPETENCY_LINK_WEIGHT = MEDIUM_COMPETENCY_LINK_WEIGHT;
    protected readonly LOW_COMPETENCY_LINK_WEIGHT = LOW_COMPETENCY_LINK_WEIGHT;
    protected readonly LOW_COMPETENCY_LINK_WEIGHT_CUT_OFF = LOW_COMPETENCY_LINK_WEIGHT_CUT_OFF; // halfway between low and medium
    protected readonly MEDIUM_COMPETENCY_LINK_WEIGHT_CUT_OFF = MEDIUM_COMPETENCY_LINK_WEIGHT_CUT_OFF;
    // halfway between medium and high

    ngOnInit(): void {
        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (!this.competencyLinks && courseId) {
            const course = this.courseStorageService.getCourse(courseId);
            // an empty array is used as fallback, if a course is cached, where no competencies have been queried
            if (course?.competencies?.length || course?.prerequisites?.length) {
                this.setCompetencyLinks([...(course.competencies ?? []), ...(course.prerequisites ?? [])]);
            } else {
                this.isLoading = true;
                this.courseCompetencyService
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
                            this.setCompetencyLinks(response.body!);
                            this.writeValue(this.selectedCompetencyLinks);
                        },
                        error: () => {
                            this.disabled = true;
                        },
                    });
            }
        }
    }

    /**
     * Set the available competencyLinks for selection
     * @param competencies The competencies of the course
     */
    setCompetencyLinks(competencies: CourseCompetency[]) {
        this.competencyLinks = competencies.map((competency) => {
            // Remove unnecessary properties
            competency.course = undefined;
            competency.userProgress = undefined;
            return new CompetencyLearningObjectLink(competency, MEDIUM_COMPETENCY_LINK_WEIGHT);
        });
        this.checkboxStates = this.competencyLinks.reduce(
            (states, competencyLink) => {
                if (competencyLink.competency?.id) {
                    states[competencyLink.competency.id] = !!this.selectedCompetencyLinks?.find((value) => value.competency?.id === competencyLink.competency?.id);
                }
                return states;
            },
            {} as Record<number, boolean>,
        );
    }

    toggleCompetency(newValue: CompetencyLearningObjectLink) {
        if (newValue.competency?.id) {
            if (this.checkboxStates[newValue.competency.id]) {
                this.selectedCompetencyLinks = this.selectedCompetencyLinks?.filter((value) => value.competency?.id !== newValue.competency?.id);
            } else {
                this.selectedCompetencyLinks = [...(this.selectedCompetencyLinks ?? []), newValue];
            }

            this.checkboxStates[newValue.competency.id] = !this.checkboxStates[newValue.competency.id];

            // make sure to do not send an empty list to server
            if (!this.selectedCompetencyLinks?.length) {
                this.selectedCompetencyLinks = undefined;
            }

            this._onChange(this.selectedCompetencyLinks);
            this.valueChange.emit(this.selectedCompetencyLinks);
        }
    }

    updateLinkWeight(link: CompetencyLearningObjectLink, value: number) {
        link.weight = value;

        this._onChange(this.selectedCompetencyLinks);
        this.valueChange.emit(this.selectedCompetencyLinks);
    }

    writeValue(value?: CompetencyLearningObjectLink[]): void {
        this.competencyLinks?.forEach((link) => {
            const selectedLink = value?.find((value) => value.competency?.id === link.competency?.id);
            link.weight = selectedLink?.weight ?? MEDIUM_COMPETENCY_LINK_WEIGHT;
        });

        if (value && this.competencyLinks) {
            // Compare the ids of the competencies instead of the whole objects
            const ids = value.map((el) => el.competency?.id);
            this.selectedCompetencyLinks = this.competencyLinks.filter((competencyLink) => ids.includes(competencyLink.competency?.id));
        } else {
            this.selectedCompetencyLinks = value ?? [];
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

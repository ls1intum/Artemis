import { ChangeDetectorRef, Component, OnInit, forwardRef, inject, input, output } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faLightbulb, faQuestionCircle, faStar } from '@fortawesome/free-solid-svg-icons';
import { HttpClient } from '@angular/common/http';
import {
    CompetencyLearningObjectLink,
    CourseCompetency,
    HIGH_COMPETENCY_LINK_WEIGHT,
    LOW_COMPETENCY_LINK_WEIGHT,
    LOW_COMPETENCY_LINK_WEIGHT_CUT_OFF,
    MEDIUM_COMPETENCY_LINK_WEIGHT,
    MEDIUM_COMPETENCY_LINK_WEIGHT_CUT_OFF,
    getIcon,
} from 'app/atlas/shared/entities/competency.model';
import { ActivatedRoute } from '@angular/router';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { finalize } from 'rxjs';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';

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
    imports: [
        FaStackComponent,
        NgbTooltip,
        FaIconComponent,
        FaStackItemSizeDirective,
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FeatureToggleHideDirective,
        ButtonComponent,
    ],
})
export class CompetencySelectionComponent implements OnInit, ControlValueAccessor {
    private route = inject(ActivatedRoute);
    private courseStorageService = inject(CourseStorageService);
    private courseCompetencyService = inject(CourseCompetencyService);
    private changeDetector = inject(ChangeDetectorRef);
    private profileService = inject(ProfileService);
    private http = inject(HttpClient);

    labelName = input<string>('');
    labelTooltip = input<string>('');
    exerciseDescription = input<string | undefined>(undefined);

    valueChange = output<CompetencyLearningObjectLink[] | undefined>();

    disabled: boolean;
    // selected competencies
    selectedCompetencyLinks?: CompetencyLearningObjectLink[];
    // all course competencies
    competencyLinks?: CompetencyLearningObjectLink[];

    isLoading = false;
    isSuggesting = false;
    checkboxStates: Record<number, boolean>;
    suggestedCompetencyIds = new Set<number>();

    getIcon = getIcon;
    faQuestionCircle = faQuestionCircle;
    faStar = faStar;
    faLightbulb = faLightbulb;

    protected readonly FeatureToggle = FeatureToggle;

    _onChange = (_value: any) => {};

    protected readonly HIGH_COMPETENCY_LINK_WEIGHT = HIGH_COMPETENCY_LINK_WEIGHT;
    protected readonly MEDIUM_COMPETENCY_LINK_WEIGHT = MEDIUM_COMPETENCY_LINK_WEIGHT;
    protected readonly LOW_COMPETENCY_LINK_WEIGHT = LOW_COMPETENCY_LINK_WEIGHT;
    protected readonly LOW_COMPETENCY_LINK_WEIGHT_CUT_OFF = LOW_COMPETENCY_LINK_WEIGHT_CUT_OFF; // halfway between low and medium
    protected readonly MEDIUM_COMPETENCY_LINK_WEIGHT_CUT_OFF = MEDIUM_COMPETENCY_LINK_WEIGHT_CUT_OFF;
    // halfway between medium and high

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    ngOnInit(): void {
        // it's an explicit design decision to not clutter every component that uses this component with the need to check if the atlas profile is enabled
        if (this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
            this.initialize();
        }
    }

    initialize(): void {
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

    registerOnTouched(_fn: any): void {}

    suggestCompetencies(): void {
        if (!this.exerciseDescription()?.trim()) {
            return;
        }

        this.isSuggesting = true;
        this.suggestedCompetencyIds.clear();

        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        const requestBody = { description: this.exerciseDescription(), course_id: courseId?.toString() };

        this.http
            .post<{ competencies: any[] }>('/api/atlas/competencies/suggest', requestBody)
            .pipe(
                finalize(() => {
                    this.isSuggesting = false;
                    this.changeDetector.detectChanges();
                }),
            )
            .subscribe({
                next: (response) => {
                    response.competencies.forEach((suggestion) => {
                        const matchingLink = this.competencyLinks?.find((link) => link.competency?.id === Number(suggestion.id));
                        if (matchingLink?.competency?.id) {
                            this.suggestedCompetencyIds.add(matchingLink.competency.id);
                        }
                    });
                    this.sortCompetenciesBySuggestion();
                },
                error: (error) => {
                    // console.error('Error getting competency suggestions:', error);
                },
            });
    }

    isSuggested(competencyId: number): boolean {
        return this.suggestedCompetencyIds.has(competencyId);
    }

    sortCompetenciesBySuggestion(): void {
        if (this.competencyLinks) {
            this.competencyLinks.sort((a, b) => {
                const aIsSuggested = a.competency?.id ? this.isSuggested(a.competency.id) : false;
                const bIsSuggested = b.competency?.id ? this.isSuggested(b.competency.id) : false;

                // Sort suggested competencies to the top
                if (aIsSuggested && !bIsSuggested) return -1;
                if (!aIsSuggested && bIsSuggested) return 1;

                // Keep original order for items with same suggestion status
                return 0;
            });
        }
    }

    setDisabledState?(isDisabled: boolean): void {
        this.disabled = isDisabled;
    }
}

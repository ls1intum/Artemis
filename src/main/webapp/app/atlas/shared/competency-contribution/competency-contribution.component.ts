import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CompetencyContributionCardDTO } from 'app/atlas/shared/entities/competency.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CompetencyContributionCardComponent } from 'app/atlas/shared/competency-contribution/competency-contribution-card/competency-contribution-card.component';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';

@Component({
    selector: 'jhi-competency-contribution',
    imports: [TranslateDirective, CompetencyContributionCardComponent, TumAetUiButtonDirective, FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './competency-contribution.component.html',
})
export class CompetencyContributionComponent {
    private readonly courseCompetencyService = inject(CourseCompetencyService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);

    courseId = input.required<number>();
    learningObjectId = input.required<number>();
    isExercise = input.required<boolean>();

    // `competencies` is assigned inside an async HTTP subscribe; both fields are read in the template,
    // so they must be signals to render under zoneless change detection.
    readonly atlasEnabled = signal(false);
    readonly competencies = signal<CompetencyContributionCardDTO[]>([]);

    protected readonly firstVisibleIndex = signal(0);
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;
    protected readonly visibleCompetencies = computed(() => {
        const competencies = this.competencies();
        if (competencies.length <= 3) {
            return competencies;
        }
        return Array.from({ length: 3 }, (_, offset) => competencies[(this.firstVisibleIndex() + offset) % competencies.length]);
    });

    protected move(direction: -1 | 1): void {
        const count = this.competencies().length;
        if (count > 3) {
            this.firstVisibleIndex.update((index) => (index + direction + count) % count);
        }
    }

    protected selectPage(index: number): void {
        this.firstVisibleIndex.set(index);
    }

    constructor() {
        effect(() => this.loadData());
    }

    private loadData(): void {
        const atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);
        this.atlasEnabled.set(atlasEnabled);

        // we can return early if atlas is not enabled
        if (!atlasEnabled) {
            return;
        }

        let observable: Observable<HttpResponse<CompetencyContributionCardDTO[]>>;
        if (this.isExercise()) {
            observable = this.courseCompetencyService.getCompetencyContributionsForExercise(this.learningObjectId());
        } else {
            observable = this.courseCompetencyService.getCompetencyContributionsForLectureUnit(this.learningObjectId());
        }
        observable.subscribe({
            next: (res) => {
                this.firstVisibleIndex.set(0);
                this.competencies.set(res.body ?? []);
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }
}

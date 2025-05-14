import { Component, effect, inject, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CompetencyContributionCardDTO } from 'app/atlas/shared/entities/competency.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CompetencyContributionCardComponent } from 'app/atlas/shared/competency-contribution/competency-contribution-card/competency-contribution-card.component';
import { CarouselModule } from 'primeng/carousel';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';

@Component({
    selector: 'jhi-competency-contribution',
    imports: [TranslateDirective, CompetencyContributionCardComponent, CarouselModule],
    templateUrl: './competency-contribution.component.html',
})
export class CompetencyContributionComponent {
    courseId = input.required<number>();
    learningObjectId = input.required<number>();
    isExercise = input.required<boolean>();

    private readonly courseCompetencyService = inject(CourseCompetencyService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);

    atlasEnabled = false;
    competencies: CompetencyContributionCardDTO[] = [];

    constructor() {
        effect(() => this.loadData());
    }

    private loadData(): void {
        this.atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);

        // we can return early if atlas is not enabled
        if (!this.atlasEnabled) {
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
                this.competencies = res.body ?? [];
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }
}

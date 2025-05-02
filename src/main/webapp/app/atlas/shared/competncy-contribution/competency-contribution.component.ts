import { Component, effect, inject, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CompetencyContributionCardDTO } from 'app/atlas/shared/entities/competency.model';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CompetencyContributionCardComponent } from 'app/atlas/shared/competncy-contribution/competncy-contribution-card/competency-contribution-card.component';
import { CarouselModule } from 'primeng/carousel';

@Component({
    selector: 'jhi-competency-contribution',
    imports: [TranslateDirective, CompetencyContributionCardComponent, CarouselModule],
    templateUrl: './competency-contribution.component.html',
    styleUrl: './competency-contribution.component.scss',
})
export class CompetencyContributionComponent {
    courseId = input.required<number>();
    learningObjectId = input.required<number>();
    isExercise = input.required<boolean>();

    private readonly courseCompetencyService = inject(CourseCompetencyService);
    private readonly alertService = inject(AlertService);

    competencies: CompetencyContributionCardDTO[] = [];

    constructor() {
        effect(() => this.loadData());
    }

    private loadData(): void {
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

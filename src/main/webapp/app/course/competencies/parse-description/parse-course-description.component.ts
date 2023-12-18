import { Component, OnInit } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { Competency } from 'app/entities/competency.model';
@Component({
    selector: 'jhi-parse-course-description',
    templateUrl: './parse-course-description.component.html',
})
export class ParseCoureDescriptionComponent implements OnInit {
    courseId: number;
    recommendations: Competency[] = [];

    constructor(
        private competencyService: CompetencyService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        //TODO: set courseId correctly
        this.courseId = 1;
    }

    getCompetencyRecommendations(courseDescription: string) {
        this.competencyService
            .getCompetenciesFromCourseDescription(courseDescription, this.courseId)
            .pipe()
            .subscribe({
                next: (res) => {
                    if (res.body) {
                        this.recommendations = res.body;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}

import { Component, OnInit } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { Competency } from 'app/entities/competency.model';
import { ActivatedRoute, Router } from '@angular/router';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormBuilder, FormGroup } from '@angular/forms';
@Component({
    selector: 'jhi-parse-course-description',
    templateUrl: './parse-course-description.component.html',
})
export class ParseCoureDescriptionComponent implements OnInit {
    courseId: number;
    recommendations: Competency[] = [];
    form: FormGroup;

    //Icons
    faTimes = faTimes;

    constructor(
        private competencyService: CompetencyService,
        private alertService: AlertService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private formBuilder: FormBuilder,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });
        this.form = this.formBuilder.group({});
    }

    getCompetencyRecommendations(courseDescription: string) {
        this.competencyService.getCompetenciesFromCourseDescription(courseDescription, this.courseId).subscribe({
            next: (res) => {
                if (res.body) {
                    this.recommendations = res.body;
                }
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    cancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    submit() {
        this.competencyService.createBulk(this.recommendations, this.courseId).subscribe({
            next: () => {
                console.log('IT WORKED HEHE :D');
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }
}

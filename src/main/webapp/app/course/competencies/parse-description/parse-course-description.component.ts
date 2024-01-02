import { Component, OnInit } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { Competency } from 'app/entities/competency.model';
import { ActivatedRoute, Router } from '@angular/router';
import { faBan, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormArray, FormBuilder, FormGroup } from '@angular/forms';
import { ButtonType } from 'app/shared/components/button.component';
import { finalize } from 'rxjs/operators';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-parse-course-description',
    templateUrl: './parse-course-description.component.html',
})
export class ParseCoureDescriptionComponent implements OnInit, ComponentCanDeactivate {
    //TODO: split up form > competencySmall & seen?
    //TODO: fix: reload is still possible, but on save it says I have changes?
    courseId: number;
    form: FormGroup;
    isLoading = false;
    seen: boolean[] = [];

    //Icons
    protected readonly faTimes = faTimes;
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;

    protected readonly ButtonType = ButtonType;

    constructor(
        private competencyService: CompetencyService,
        private alertService: AlertService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private formBuilder: FormBuilder,
        private modalService: NgbModal,
        private artemisTranslatePipe: ArtemisTranslatePipe,
    ) {}

    canDeactivate(): boolean {
        return !this.isLoading && !(this.recommendations.length > 0);
    }

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });
        this.form = this.formBuilder.group({
            recommendations: this.formBuilder.array([]),
        });
    }

    getCompetencyRecommendations(courseDescription: string) {
        this.isLoading = true;
        this.competencyService
            .getCompetenciesFromCourseDescription(courseDescription, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (res) => {
                    if (res.body) {
                        for (const c of res.body) {
                            this.recommendations.push(
                                this.formBuilder.group({
                                    title: [c.title],
                                    description: [c.description],
                                    taxonomy: [c.taxonomy],
                                }),
                            );
                            this.seen.push(false);
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    get recommendations() {
        return this.form.get('recommendations') as FormArray;
    }

    getRecommendationFormGroup(index: number) {
        return this.recommendations.at(index) as FormGroup;
    }

    cancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    onDelete(index: number) {
        this.recommendations.removeAt(index);
        this.seen.splice(index, 1);
    }

    onSeen(index: number) {
        this.seen[index] = true;
    }

    submit() {
        //TODO: make a method out of this?
        if (this.seen.includes(false)) {
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'sm' });
            modalRef.componentInstance.title = 'artemisApp.competency.parseDescription.unseenModalTitle';
            modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.competency.parseDescription.unseenModalText');
            modalRef.result.then(this.saveRecommendations.bind(this));
        } else {
            this.saveRecommendations();
        }
    }

    saveRecommendations() {
        const competenciesToSave: Competency[] = this.recommendations.value.map((c: any) => this.parseCompetency(c));
        this.competencyService.createBulk(competenciesToSave, this.courseId).subscribe({
            next: () => {
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    parseCompetency(c: any) {
        const result = new Competency();
        result.description = c.description;
        result.title = c.title;
        result.taxonomy = c.taxonomy;
        return result;
    }
}

import { Component, OnInit } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { ActivatedRoute, Router } from '@angular/router';
import { faBan, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormArray, FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { ButtonType } from 'app/shared/components/button.component';
import { finalize } from 'rxjs/operators';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export type CompetencyFormControlsWithViewed = {
    competency: FormGroup<CompetencyFormControls>;
    viewed: FormControl<boolean | null>;
};

export type CompetencyFormControls = {
    title: FormControl<string | null | undefined>;
    description: FormControl<string | null | undefined>;
    taxonomy: FormControl<CompetencyTaxonomy | null | undefined>;
};

@Component({
    selector: 'jhi-parse-course-description',
    templateUrl: './parse-course-description.component.html',
})
export class ParseCourseDescriptionComponent implements OnInit, ComponentCanDeactivate {
    courseId: number;
    isLoading = false;
    submitted: boolean = false;
    form = new FormGroup({ competencies: new FormArray<FormGroup<CompetencyFormControlsWithViewed>>([]) });

    //Icons
    protected readonly faTimes = faTimes;
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;

    //Other constants
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

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });
    }

    /**
     * Only allow to leave page after submitting or if no pending changes exist
     */
    canDeactivate(): boolean {
        return this.submitted || (!this.isLoading && !(this.competencies.length > 0));
    }

    /**
     * Parses competency recommendations from the given course description and adds them to the form
     * @param courseDescription
     */
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
                    res.body?.forEach((competency) => this.addCompetencyToForm(competency));
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    /**
     * Adds a competency to the form
     * @param competency
     * @private
     */
    private addCompetencyToForm(competency: Competency) {
        const formGroup: FormGroup<CompetencyFormControlsWithViewed> = this.formBuilder.group({
            competency: this.formBuilder.group({
                title: [competency.title],
                description: [competency.description],
                taxonomy: [competency.taxonomy],
            }),
            viewed: [false],
        });
        this.competencies.push(formGroup);
    }

    /**
     * Handles deletion of a competency recommendation
     * @param index of the competency
     */
    onDelete(index: number) {
        this.competencies.removeAt(index);
    }

    /**
     * Cancels the parsing and navigates back
     */
    onCancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    /**
     * Submits and opens an additional confirmation modal if needed
     */
    onSubmit() {
        if (!this.isSubmitPossibleWithoutConfirmation()) {
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'sm' });
            modalRef.componentInstance.title = 'artemisApp.competency.parseDescription.confirmationModalTitle';
            modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.competency.parseDescription.confirmationModalText');
            modalRef.result.then(this.save.bind(this));
        } else {
            this.save();
        }
    }

    /**
     * Saves the competency recommendations as competencies and navigates back
     */
    save() {
        const competenciesToSave = this.competencies.value.map((c) => c.competency as Competency);
        this.competencyService.createBulk(competenciesToSave, this.courseId).subscribe({
            next: () => {
                this.submitted = true;
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Only allows submitting if no form controls have validation errors
     */
    get isSubmitPossible() {
        return !this.form.invalid;
    }

    /**
     * Needs confirmation to submit if not all competency recommendations were viewed
     */
    private isSubmitPossibleWithoutConfirmation() {
        const viewedArray = this.form.value.competencies?.map((c) => c.viewed);
        return !viewedArray?.includes(false);
    }

    //getter for form controls
    get competencies() {
        return this.form.controls.competencies;
    }
}

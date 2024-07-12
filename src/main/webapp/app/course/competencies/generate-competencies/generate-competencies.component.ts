import { Component, HostListener, OnInit } from '@angular/core';
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
import { TranslateService } from '@ngx-translate/core';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

export type CompetencyFormControlsWithViewed = {
    competency: FormGroup<CompetencyFormControls>;
    viewed: FormControl<boolean>;
};

export type CompetencyFormControls = {
    title: FormControl<string | undefined>;
    description: FormControl<string | undefined>;
    taxonomy: FormControl<CompetencyTaxonomy | undefined>;
};

@Component({
    selector: 'jhi-generate-competencies',
    templateUrl: './generate-competencies.component.html',
})
export class GenerateCompetenciesComponent implements OnInit, ComponentCanDeactivate {
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
    readonly documentationType: DocumentationType = 'GenerateCompetencies';

    constructor(
        private competencyService: CompetencyService,
        private alertService: AlertService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private formBuilder: FormBuilder,
        private modalService: NgbModal,
        private artemisTranslatePipe: ArtemisTranslatePipe,
        private translateService: TranslateService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });
    }

    /**
     * Parses competency recommendations from the given course description and adds them to the form
     * @param courseDescription
     */
    getCompetencyRecommendations(courseDescription: string) {
        this.isLoading = true;
        this.competencyService
            .generateCompetenciesFromCourseDescription(courseDescription, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (res) => {
                    if (res.body?.length && res.body.length > 0) {
                        this.alertService.success('artemisApp.competency.generate.courseDescription.success', { noOfCompetencies: res.body.length });
                        res.body?.forEach((competency) => this.addCompetencyToForm(competency));
                    } else {
                        this.alertService.warning('artemisApp.competency.generate.courseDescription.warning');
                    }
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
        const formGroup: FormGroup<CompetencyFormControlsWithViewed> = this.formBuilder.nonNullable.group({
            competency: this.formBuilder.nonNullable.group({
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
        const competencyTitle = this.competencies.at(index).controls.competency.controls.title.getRawValue() ?? '';
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.competency.generate.deleteModalTitle';
        modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.competency.generate.deleteModalText', { title: competencyTitle });
        modalRef.result.then(() => this.competencies.removeAt(index));
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
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
            modalRef.componentInstance.title = 'artemisApp.competency.generate.saveModalTitle';
            modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.competency.generate.saveModalText');
            modalRef.result.then(this.save.bind(this));
        } else {
            this.save();
        }
    }

    /**
     * Saves the competency recommendations as competencies and navigates back
     */
    save() {
        const competenciesToSave = this.competencies.getRawValue().map((c) => c.competency as Competency);
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
        const viewedArray = this.form.getRawValue().competencies?.map((c) => c.viewed);
        return !viewedArray?.includes(false);
    }

    //getter for form controls
    get competencies() {
        return this.form.controls.competencies;
    }

    /**
     * Only allow to leave page after submitting or if no pending changes exist
     */
    canDeactivate(): boolean {
        return this.submitted || (!this.isLoading && this.competencies.length === 0);
    }

    get canDeactivateWarning(): string {
        return this.translateService.instant('pendingChanges');
    }

    /**
     * Only allow to refresh the page if no pending changes exist
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any) {
        if (!this.canDeactivate()) {
            event.returnValue = this.canDeactivateWarning;
        }
    }
}

import { Component, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { ActivatedRoute, Router } from '@angular/router';
import { faBan, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormArray, FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { ButtonType } from 'app/shared/components/button.component';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, firstValueFrom, map } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStageDTO, IrisStageStateDTO } from 'app/entities/iris/iris-stage-dto.model';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseDescriptionFormComponent } from 'app/course/competencies/generate-competencies/course-description-form.component';

export type CompetencyFormControlsWithViewed = {
    competency: FormGroup<CompetencyFormControls>;
    viewed: FormControl<boolean>;
};

export type CompetencyFormControls = {
    title: FormControl<string | undefined>;
    description: FormControl<string | undefined>;
    taxonomy: FormControl<CompetencyTaxonomy | undefined>;
};

export type CompetencyRecommendation = {
    title?: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
};

type CompetencyGenerationStatusUpdate = {
    stages: IrisStageDTO[];
    result?: CompetencyRecommendation[];
};

@Component({
    selector: 'jhi-generate-competencies',
    templateUrl: './generate-competencies.component.html',
    standalone: true,
    imports: [ArtemisSharedCommonModule, ArtemisSharedComponentModule, ArtemisCompetenciesModule],
})
export class GenerateCompetenciesComponent implements OnInit, ComponentCanDeactivate {
    private courseManagementService = inject(CourseManagementService);
    private courseCompetencyService = inject(CourseCompetencyService);
    private competencyService = inject(CompetencyService);
    private alertService = inject(AlertService);
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private formBuilder = inject(FormBuilder);
    private modalService = inject(NgbModal);
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private translateService = inject(TranslateService);
    private jhiWebsocketService = inject(JhiWebsocketService);

    @ViewChild(CourseDescriptionFormComponent) courseDescriptionForm: CourseDescriptionFormComponent;

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

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            firstValueFrom(this.courseManagementService.find(this.courseId))
                .then((course) => this.courseDescriptionForm.setCourseDescription(course.body?.description ?? ''))
                .catch((res: HttpErrorResponse) => onError(this.alertService, res));
        });
    }

    /**
     * Parses competency recommendations from the given course description and adds them to the form
     * @param courseDescription
     */
    getCompetencyRecommendations(courseDescription: string) {
        this.isLoading = true;
        this.getCurrentCompetencies().subscribe((currentCompetencies) => {
            this.courseCompetencyService.generateCompetenciesFromCourseDescription(this.courseId, courseDescription, currentCompetencies).subscribe({
                next: () => {
                    const websocketTopic = `/user/topic/iris/competencies/${this.courseId}`;
                    this.jhiWebsocketService.subscribe(websocketTopic);
                    this.jhiWebsocketService.receive(websocketTopic).subscribe({
                        next: (update: CompetencyGenerationStatusUpdate) => {
                            if (update.result) {
                                for (const competency of update.result) {
                                    this.addCompetencyToForm(competency);
                                }
                            }
                            if (update.stages.every((stage) => stage.state === IrisStageStateDTO.DONE)) {
                                this.alertService.success('artemisApp.competency.generate.courseDescription.success', { noOfCompetencies: update.result?.length });
                            } else if (update.stages.some((stage) => stage.state === IrisStageStateDTO.ERROR)) {
                                this.alertService.warning('artemisApp.competency.generate.courseDescription.warning');
                            }
                            if (update.stages.every((stage) => stage.state !== IrisStageStateDTO.NOT_STARTED && stage.state !== IrisStageStateDTO.IN_PROGRESS)) {
                                this.jhiWebsocketService.unsubscribe(websocketTopic);
                                this.isLoading = false;
                            }
                        },
                        error: (res: HttpErrorResponse) => {
                            onError(this.alertService, res);
                            this.jhiWebsocketService.unsubscribe(websocketTopic);
                            this.isLoading = false;
                        },
                    });
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.isLoading = false;
                },
            });
        });
    }

    /**
     * Returns the title, description, and taxonomy of all current competencies saved in this course,
     * and the competency recommendations that are currently in the form.
     * @private
     */
    private getCurrentCompetencies(): Observable<CompetencyRecommendation[]> {
        const currentCompetencySuggestions = this.competencies.getRawValue().map((c) => c.competency);
        const courseCompetenciesObservable = this.courseCompetencyService.getAllForCourse(this.courseId);
        if (courseCompetenciesObservable) {
            return courseCompetenciesObservable.pipe(
                map((competencies) => competencies.body?.map((c) => ({ title: c.title, description: c.description, taxonomy: c.taxonomy }))),
                map((competencies) => currentCompetencySuggestions.concat(competencies ?? [])),
            );
        }
        return new Observable<CompetencyRecommendation[]>((subscriber) => {
            subscriber.next(currentCompetencySuggestions);
            subscriber.complete();
        });
    }

    /**
     * Adds a competency to the form
     * @param competency
     * @private
     */
    private addCompetencyToForm(competency: CompetencyRecommendation) {
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
        const competenciesToSave = this.competencies.getRawValue().map((c) => Object.assign(new Competency(), c.competency));
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

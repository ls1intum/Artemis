import { Component, Input, OnInit } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';
import { Competency } from 'app/entities/competency.model';
import { CompetencyFormData } from 'app/course/competencies/competency-form/competency-form.component';
import { onError } from 'app/shared/util/global.utils';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { faLink, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from 'app/lecture/lecture.service';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { finalize } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';

@Component({
    selector: 'jhi-lecture-update-wizard-competencies',
    templateUrl: './lecture-wizard-competencies.component.html',
    styleUrls: ['./lecture-wizard-competencies.component.scss'],
})
export class LectureUpdateWizardCompetenciesComponent implements OnInit {
    @Input() currentStep: number;
    @Input() lecture: Lecture;
    @Input() isSaving: boolean;

    isAddingCompetency: boolean;
    isLoadingCompetencyForm: boolean;
    isLoadingCompetencies: boolean;
    isEditingCompetency: boolean;
    isConnectingCompetency: boolean;

    currentlyProcessedCompetency: Competency;
    competencies: Competency[] = [];
    competencyFormData: CompetencyFormData;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faPencilAlt = faPencilAlt;
    faLink = faLink;

    constructor(
        protected alertService: AlertService,
        protected lectureService: LectureService,
        protected competencyService: CompetencyService,
        protected translateService: TranslateService,
    ) {}

    ngOnInit() {
        this.loadCompetencies();
    }

    showCreateCompetency() {
        this.isLoadingCompetencyForm = true;
        this.isConnectingCompetency = false;
        this.isAddingCompetency = !this.isAddingCompetency;
        this.competencyFormData = {
            id: undefined,
            title: undefined,
            description: undefined,
            taxonomy: undefined,
            connectedLectureUnits: undefined,
        };

        this.subscribeToLoadUnitResponse(this.lectureService.findWithDetails(this.lecture.id!));
    }

    protected subscribeToLoadUnitResponse(result: Observable<HttpResponse<Lecture>>) {
        result.subscribe({
            next: (response: HttpResponse<Lecture>) => this.onLoadUnitSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onLoadError(error),
        });
    }

    protected subscribeToLoadCompetenciesResponse(result: Observable<HttpResponse<Competency[]>>) {
        result.subscribe({
            next: (response: HttpResponse<Competency[]>) => this.onLoadCompetenciesSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onLoadError(error),
        });
    }

    /**
     * Action on successful lecture unit fetch
     */
    protected onLoadUnitSuccess(lecture: Lecture) {
        this.lecture = lecture;

        this.isLoadingCompetencyForm = false;
    }

    /**
     * Action on successful competencies fetch
     */
    protected onLoadCompetenciesSuccess(competencies: Competency[]) {
        this.isLoadingCompetencies = false;

        this.competencies = competencies;
    }

    /**
     * Action on unsuccessful fetch
     * @param error the error handed to the alert service
     */
    protected onLoadError(error: HttpErrorResponse) {
        this.isSaving = false;
        this.isLoadingCompetencyForm = false;
        this.isLoadingCompetencies = false;

        onError(this.alertService, error);
    }

    onCompetencyFormSubmitted(formData: CompetencyFormData) {
        if (this.isEditingCompetency) {
            this.editCompetency(formData);
        } else {
            this.createCompetency(formData);
        }
    }

    createCompetency(formData: CompetencyFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, taxonomy, connectedLectureUnits } = formData;
        this.currentlyProcessedCompetency = new Competency();

        this.currentlyProcessedCompetency.title = title;
        this.currentlyProcessedCompetency.description = description;
        this.currentlyProcessedCompetency.taxonomy = taxonomy;
        this.currentlyProcessedCompetency.lectureUnits = connectedLectureUnits;

        this.isLoadingCompetencyForm = true;

        this.competencyService
            .create(this.currentlyProcessedCompetency!, this.lecture.course!.id!)
            .pipe(
                finalize(() => {
                    this.isLoadingCompetencyForm = false;
                }),
            )
            .subscribe({
                next: (response: HttpResponse<Competency>) => {
                    this.isAddingCompetency = false;

                    // The rest api is returning lecture units and exercises separately after creating/editing but we
                    // need the unit to show it in the table as connected. Since it's only for showing it, as a
                    // workaround we take the unit from the lecture which is the same one.
                    const newCompetency = response.body!;
                    const exerciseUnits = this.lecture.lectureUnits?.filter((unit: ExerciseUnit) => newCompetency.exercises?.find((exercise) => exercise.id === unit.exercise?.id));
                    newCompetency.lectureUnits = newCompetency.lectureUnits?.concat(exerciseUnits ?? []);

                    this.competencies = this.competencies.concat(newCompetency);

                    this.alertService.success(`Competency ${this.currentlyProcessedCompetency.title} was successfully created.`);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    editCompetency(formData: CompetencyFormData) {
        const { title, description, taxonomy, connectedLectureUnits } = formData;

        this.currentlyProcessedCompetency.title = title;
        this.currentlyProcessedCompetency.description = description;
        this.currentlyProcessedCompetency.taxonomy = taxonomy;
        this.currentlyProcessedCompetency.lectureUnits = connectedLectureUnits;

        this.isLoadingCompetencyForm = true;

        this.competencyService
            .update(this.currentlyProcessedCompetency, this.lecture.course!.id!)
            .pipe(
                finalize(() => {
                    this.isLoadingCompetencyForm = false;
                }),
            )
            .subscribe({
                next: (response: HttpResponse<Competency>) => {
                    this.isEditingCompetency = false;
                    this.isConnectingCompetency = false;

                    // The rest api is returning lecture units and exercises separately after creating/editing but we
                    // need the unit to show it in the table as connected. Since it's only for showing it, as a
                    // workaround we take the unit from the lecture which is the same one.
                    const editedCompetency = response.body!;
                    const exerciseUnits = this.lecture.lectureUnits?.filter((unit: ExerciseUnit) =>
                        editedCompetency.exercises?.find((exercise) => exercise.id === unit.exercise?.id),
                    );
                    editedCompetency.lectureUnits = editedCompetency.lectureUnits?.concat(exerciseUnits ?? []);

                    const index = this.competencies.findIndex((competency) => competency.id === this.currentlyProcessedCompetency.id);
                    if (index === -1) {
                        this.competencies = this.competencies.concat(editedCompetency);
                    } else {
                        this.competencies[index] = editedCompetency;
                    }

                    this.alertService.success(`Competency ${this.currentlyProcessedCompetency.title} was successfully edited.`);
                    this.currentlyProcessedCompetency = new Competency();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    trackCompetencyId(index: number, item: Competency) {
        return item.id;
    }

    loadCompetencies() {
        this.isLoadingCompetencies = true;
        this.isLoadingCompetencyForm = true;

        this.subscribeToLoadCompetenciesResponse(this.competencyService.getAllForCourse(this.lecture.course!.id!));
        this.subscribeToLoadUnitResponse(this.lectureService.findWithDetails(this.lecture.id!));
    }

    getConnectedUnitsForCompetency(competency: Competency) {
        const units = competency.lectureUnits?.filter((unit) => this.lecture.lectureUnits?.find((u) => u.id === unit.id));

        if (units === undefined || units.length === 0) {
            return this.translateService.instant('artemisApp.lecture.wizardMode.competencyNoConnectedUnits');
        }

        return units.map((unit) => unit.name).join(', ');
    }

    startEditCompetency(competency: Competency) {
        const connectedUnits: LectureUnit[] = [];
        competency.lectureUnits?.forEach((unit) => connectedUnits.push(Object.assign({}, unit)));

        this.isLoadingCompetencyForm = true;
        this.isEditingCompetency = true;
        this.currentlyProcessedCompetency = competency;

        this.competencyFormData = {
            id: competency.id,
            title: competency.title,
            description: competency.description,
            taxonomy: competency.taxonomy,
            connectedLectureUnits: connectedUnits,
        };

        this.subscribeToLoadUnitResponse(this.lectureService.findWithDetails(this.lecture.id!));
    }

    startConnectingCompetency(competency: Competency) {
        this.isConnectingCompetency = true;

        this.startEditCompetency(competency);
    }

    deleteCompetency(competency: Competency) {
        this.competencyService.delete(competency.id!, this.lecture.course!.id!).subscribe({
            next: () => {
                this.competencies = this.competencies.filter((existingCompetency) => existingCompetency.id !== competency.id);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    onCompetencyFormCanceled() {
        this.isAddingCompetency = false;
        this.isEditingCompetency = false;
        this.isConnectingCompetency = false;
        this.isLoadingCompetencyForm = false;

        this.currentlyProcessedCompetency = new Competency();
    }
}

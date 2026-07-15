import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import dayjs from 'dayjs/esm';
import { Subject } from 'rxjs';

import { faBan, faPencilAlt, faPlus, faSave, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { onError } from 'app/foundation/util/global.utils';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { SortService } from 'app/foundation/service/sort.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';

@Component({
    selector: 'jhi-presentation-assessment-management',
    templateUrl: './presentation-assessment-management.component.html',
    styleUrl: './presentation-assessment-management.component.scss',
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        DeleteButtonDirective,
        SortDirective,
        SortByDirective,
        ArtemisDatePipe,
        FormDateTimePickerComponent,
        CourseTitleBarActionsDirective,
    ],
})
export class PresentationAssessmentManagementComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly formBuilder = inject(FormBuilder);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly alertService = inject(AlertService);
    private readonly sortService = inject(SortService);

    protected readonly faBan = faBan;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPlus = faPlus;
    protected readonly faSave = faSave;
    protected readonly faSort = faSort;
    protected readonly faTrash = faTrash;

    readonly courseId = signal<number>(0);
    readonly presentationAssessments = signal<PresentationAssessment[]>([]);
    readonly editedAssessment = signal<PresentationAssessment | undefined>(undefined);
    readonly isSaving = signal(false);
    readonly showForm = signal(false);

    predicate = 'presentationDate';
    ascending = true;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    editForm = this.formBuilder.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        description: ['', [Validators.maxLength(1000)]],
        maxPoints: [0, [Validators.required, Validators.min(0.01)]],
        resultPoints: [undefined as number | undefined, [Validators.min(0)]],
        presentationDate: [undefined as dayjs.Dayjs | undefined],
    });

    ngOnInit(): void {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId')));
        this.loadAll();
    }

    loadAll(): void {
        this.presentationAssessmentService.findAllByCourseId(this.courseId()).subscribe({
            next: (res: HttpResponse<PresentationAssessment[]>) => {
                this.presentationAssessments.set(res.body ?? []);
                this.sortRows();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    startCreate(): void {
        this.editedAssessment.set(undefined);
        this.editForm.reset({
            title: '',
            description: '',
            maxPoints: 20,
            resultPoints: undefined,
            presentationDate: undefined,
        });
        this.showForm.set(true);
    }

    startEdit(presentationAssessment: PresentationAssessment): void {
        this.editedAssessment.set(presentationAssessment);
        this.editForm.reset({
            title: presentationAssessment.title ?? '',
            description: presentationAssessment.description ?? '',
            maxPoints: presentationAssessment.maxPoints ?? 0,
            resultPoints: presentationAssessment.resultPoints,
            presentationDate: presentationAssessment.presentationDate,
        });
        this.showForm.set(true);
    }

    cancelEdit(): void {
        this.showForm.set(false);
        this.editedAssessment.set(undefined);
        this.editForm.reset();
    }

    save(): void {
        if (this.editForm.invalid) {
            this.editForm.markAllAsTouched();
            return;
        }

        this.isSaving.set(true);
        const presentationAssessment = this.createFromForm();
        const request = presentationAssessment.id
            ? this.presentationAssessmentService.update(this.courseId(), presentationAssessment)
            : this.presentationAssessmentService.create(this.courseId(), presentationAssessment);

        request.subscribe({
            next: () => this.onSaveSuccess(Boolean(presentationAssessment.id)),
            error: (res: HttpErrorResponse) => this.onSaveError(res),
        });
    }

    deletePresentationAssessment(presentationAssessment: PresentationAssessment): void {
        if (!presentationAssessment.id) {
            return;
        }

        this.presentationAssessmentService.delete(this.courseId(), presentationAssessment.id).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.presentationAssessments.set(this.presentationAssessments().filter((assessment) => assessment.id !== presentationAssessment.id));
                this.alertService.success('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    sortRows(): void {
        const sortedAssessments = [...this.presentationAssessments()];
        this.sortService.sortByProperty(sortedAssessments, this.predicate, this.ascending);
        this.presentationAssessments.set(sortedAssessments);
    }

    private createFromForm(): PresentationAssessment {
        const formValue = this.editForm.getRawValue();
        return {
            id: this.editedAssessment()?.id,
            title: formValue.title?.trim(),
            description: formValue.description ?? undefined,
            maxPoints: formValue.maxPoints ?? undefined,
            resultPoints: formValue.resultPoints ?? undefined,
            presentationDate: formValue.presentationDate ? dayjs(formValue.presentationDate) : undefined,
            courseId: this.courseId(),
        };
    }

    private onSaveSuccess(isUpdate: boolean): void {
        this.isSaving.set(false);
        this.showForm.set(false);
        this.editedAssessment.set(undefined);
        this.alertService.success(isUpdate ? 'artemisApp.presentationAssessment.updated' : 'artemisApp.presentationAssessment.created');
        this.loadAll();
    }

    private onSaveError(error: HttpErrorResponse): void {
        this.isSaving.set(false);
        onError(this.alertService, error);
    }
}

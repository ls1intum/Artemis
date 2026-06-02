import { Component, effect, inject, signal, viewChild } from '@angular/core';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import { CompetencyRelationDTO, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';

import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { CourseCompetencyRelationFormComponent } from 'app/atlas/manage/course-competency-relation-form/course-competency-relation-form.component';
import { CourseCompetenciesRelationGraphComponent } from '../course-competencies-relation-graph/course-competencies-relation-graph.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export interface CourseCompetenciesRelationModalData {
    courseId: number;
    courseCompetencies: CourseCompetency[];
}

@Component({
    selector: 'jhi-course-competencies-relation-modal',
    imports: [CourseCompetenciesRelationGraphComponent, CourseCompetencyRelationFormComponent, TranslateDirective],
    templateUrl: './course-competencies-relation-modal.component.html',
    styleUrl: './course-competencies-relation-modal.component.scss',
})
export class CourseCompetenciesRelationModalComponent {
    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService = inject(AlertService);
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig, { optional: true });

    private readonly courseCompetencyRelationFormComponent = viewChild.required(CourseCompetencyRelationFormComponent);

    readonly courseId = signal<number>(0);
    readonly courseCompetencies = signal<CourseCompetency[]>([]);

    readonly selectedRelationId = signal<number | undefined>(undefined);

    readonly isLoading = signal<boolean>(false);
    readonly relations = signal<CompetencyRelationDTO[]>([]);

    constructor() {
        const data = this.dialogConfig?.data as CourseCompetenciesRelationModalData | undefined;
        if (!data) {
            // Fail closed: without a payload we have no course context, so keep safe defaults and skip loading relations.
            return;
        }

        this.courseId.set(data.courseId);
        this.courseCompetencies.set(data.courseCompetencies ?? []);

        effect(() => this.loadRelations(this.courseId()));
    }

    private async loadRelations(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const relations = await this.courseCompetencyApiService.getCourseCompetencyRelationsByCourseId(courseId);
            this.relations.set(relations);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    protected selectCourseCompetency(courseCompetencyId: number) {
        this.courseCompetencyRelationFormComponent().selectCourseCompetency(courseCompetencyId);
    }

    protected closeModal(): void {
        this.dialogRef.close();
    }
}

import { Component, computed, effect, inject, signal } from '@angular/core';
import { CourseCompetenciesManagementTableComponent } from 'app/course/competencies/components/competencies-management-table/course-competencies-management-table.component';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { Competency, CompetencyRelationDTO, CourseCompetencyType, dtoToCompetencyRelation } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseCompetenciesRelationGraphComponent } from 'app/course/competencies/components/course-competencies-relation-graph/course-competencies-relation-graph.component';

@Component({
    selector: 'jhi-course-competencies-instructor-page',
    standalone: true,
    imports: [CourseCompetenciesManagementTableComponent, ArtemisSharedCommonModule, CourseCompetenciesRelationGraphComponent],
    templateUrl: './course-competencies-instructor-page.component.html',
    styleUrl: './course-competencies-instructor-page.component.scss',
})
export class CourseCompetenciesInstructorPageComponent {
    protected readonly CourseCompetencyType = CourseCompetencyType;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService = inject(AlertService);
    private readonly featureToggleService = inject(FeatureToggleService);

    readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });

    readonly competencies = signal<Competency[]>([]);
    readonly prerequisites = signal<Prerequisite[]>([]);
    readonly courseCompetencies = computed(() => [...this.competencies(), ...this.prerequisites()]);
    readonly relations = signal<CompetencyRelationDTO[]>([]);

    readonly standardizedCompetenciesEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.StandardizedCompetencies), { requireSync: true });

    readonly isLoading = signal<boolean>(false);

    constructor() {
        effect(() => this.loadData(this.courseId()), { allowSignalWrites: true });
    }

    private async loadData(courseId: number) {
        try {
            this.isLoading.set(true);
            await this.loadCourseCompetencies(courseId);
            await this.loadCourseCompetencyRelations(courseId);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    private async loadCourseCompetencies(courseId: number) {
        const courseCompetencies = await this.courseCompetencyApiService.getCourseCompetenciesByCourseId(courseId);
        this.competencies.set(courseCompetencies.filter((competency) => competency.type === CourseCompetencyType.COMPETENCY));
        this.prerequisites.set(courseCompetencies.filter((competency) => competency.type === CourseCompetencyType.PREREQUISITE));
    }

    private async loadCourseCompetencyRelations(courseId: number) {
        const courseCompetencyRelations = await this.courseCompetencyApiService.getCourseCompetencyRelationsByCourseId(courseId);
        this.relations.set(courseCompetencyRelations.map(dtoToCompetencyRelation));
    }
}

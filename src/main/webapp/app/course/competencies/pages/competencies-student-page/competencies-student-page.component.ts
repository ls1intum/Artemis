import { Component, computed, effect, inject, signal } from '@angular/core';
import { ArtemisSidebarModule } from 'app/shared/sidebar/sidebar.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { CompetencyApiService } from 'app/course/competencies/services/competency-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { Competency, getIcon } from 'app/entities/competency.model';
import { PrerequisiteApiService } from 'app/course/competencies/services/prerequisite-api.service';
import { onError } from 'app/shared/util/global.utils';
import { Prerequisite } from 'app/entities/prerequisite.model';

@Component({
    selector: 'jhi-competencies-student-page',
    standalone: true,
    imports: [ArtemisSidebarModule, ArtemisSharedCommonModule, RouterOutlet],
    templateUrl: './competencies-student-page.component.html',
})
export class CompetenciesStudentPageComponent {
    private readonly courseCompetenciesKey = 'course-competencies';

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly alertService = inject(AlertService);

    private readonly competencyApiService = inject(CompetencyApiService);
    private readonly prerequisiteApiService = inject(PrerequisiteApiService);

    private readonly courseOverviewService = inject(CourseOverviewService);

    readonly collapseState = signal<CollapseState>({
        competencies: true,
        prerequisites: false,
    }).asReadonly();

    private readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });

    readonly isCollapsed = signal<boolean>(this.courseOverviewService.getSidebarCollapseStateFromStorage(this.courseCompetenciesKey));
    readonly isLoading = signal<boolean>(false);

    readonly competencies = signal<Competency[]>([]);
    readonly prerequisites = signal<Prerequisite[]>([]);

    constructor() {
        // Fetch data when the course id is available
        effect(() => this.loadData(this.courseId()), { allowSignalWrites: true });
        // Navigate to the first competency when the competencies are loaded
        effect(() => this.navigateToFirstCompetency(this.competencies()));
    }

    private readonly competencySidebarCards = computed(() => {
        return this.competencies().map(
            (competency) =>
                <SidebarCardElement>{
                    id: competency.id,
                    title: competency.title,
                    size: 'M',
                    icon: getIcon(competency.taxonomy),
                },
        );
    });

    private readonly prerequisitesSidebarCards = computed(() => {
        return this.prerequisites().map(
            (prerequisite) =>
                <SidebarCardElement>{
                    id: prerequisite.id,
                    title: prerequisite.title,
                    size: 'M',
                    icon: getIcon(prerequisite.taxonomy),
                },
        );
    });

    readonly sidebarData = computed(() => {
        return <SidebarData>{
            storageId: 'course-competency',
            groupByCategory: true,
            ungroupedData: [...this.competencySidebarCards(), ...this.prerequisitesSidebarCards()],
            groupedData: <AccordionGroups>{
                competencies: {
                    entityData: this.competencySidebarCards(),
                },
                prerequisites: {
                    entityData: this.prerequisitesSidebarCards(),
                },
            },
        };
    });

    private navigateToFirstCompetency(competencies: Competency[]) {
        if (competencies.length > 0) {
            this.router.navigate([this.competencies().first()!.id], { relativeTo: this.activatedRoute });
        }
    }

    async loadData(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            await Promise.all([this.loadCompetencies(courseId), this.loadPrerequisites(courseId)]);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    private async loadPrerequisites(courseId: number): Promise<void> {
        const prerequisites = await this.prerequisiteApiService.getPrerequisitesByCourseId(courseId);
        this.prerequisites.set(prerequisites);
    }

    private async loadCompetencies(courseId: number): Promise<void> {
        const competencies = await this.competencyApiService.getCompetenciesByCourseId(courseId);
        this.competencies.set(competencies);
    }

    toggleSidebar(): void {
        this.isCollapsed.update((isCollapsed) => !isCollapsed);
        this.courseOverviewService.setSidebarCollapseState(this.courseCompetenciesKey, this.isCollapsed());
    }
}

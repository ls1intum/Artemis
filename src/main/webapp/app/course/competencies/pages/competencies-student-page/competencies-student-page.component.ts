import { Component, computed, effect, inject, signal } from '@angular/core';
import { ArtemisSidebarModule } from 'app/shared/sidebar/sidebar.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { ActivatedRoute, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { CompetencyApiService } from 'app/course/competencies/services/competency-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { Competency, getIcon } from 'app/entities/competency.model';
import { DifficultyLevel } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-competencies-student-page',
    standalone: true,
    imports: [ArtemisSidebarModule, ArtemisSharedCommonModule, RouterOutlet],
    templateUrl: './competencies-student-page.component.html',
})
export class CompetenciesStudentPageComponent {
    private readonly competenciesKey = 'competencies';

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly alertService = inject(AlertService);
    private readonly competencyApiService = inject(CompetencyApiService);
    private readonly courseOverviewService = inject(CourseOverviewService);

    readonly collapseState = signal(<CollapseState>{
        competencies: true,
    }).asReadonly();

    private readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });

    readonly isCollapsed = signal<boolean>(this.courseOverviewService.getSidebarCollapseStateFromStorage(this.competenciesKey));
    readonly isLoading = signal<boolean>(false);
    readonly competencies = signal<Competency[]>([]);

    constructor() {
        // Fetch competencies when the course id is available
        effect(() => this.loadCompetencies(this.courseId()), { allowSignalWrites: true });
    }

    private readonly competencySidebarCards = computed(() => {
        return this.competencies().map(
            (competency) =>
                <SidebarCardElement>{
                    id: competency.id,
                    title: competency.title,
                    size: 'M',
                    icon: getIcon(competency.taxonomy),
                    difficulty: DifficultyLevel.EASY,
                },
        );
    });

    readonly sidebarData = computed(() => {
        return <SidebarData>{
            storageId: 'competency',
            groupByCategory: true,
            ungroupedData: this.competencySidebarCards(),
            groupedData: <AccordionGroups>{
                competencies: {
                    entityData: this.competencySidebarCards(),
                },
            },
        };
    });

    async loadCompetencies(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const competencies = await this.competencyApiService.getCompetenciesByCourseId(courseId);
            this.competencies.set(competencies);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    toggleSidebar(): void {
        this.isCollapsed.update((isCollapsed) => !isCollapsed);
        this.courseOverviewService.setSidebarCollapseState(this.competenciesKey, this.isCollapsed());
    }
}

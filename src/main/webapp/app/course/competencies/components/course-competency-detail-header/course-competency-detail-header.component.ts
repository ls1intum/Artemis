import { Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { CourseCompetency, getIcon } from 'app/entities/competency.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import dayjs from 'dayjs';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@Component({
    selector: 'jhi-course-competency-detail-header',
    standalone: true,
    imports: [ArtemisSharedCommonModule, ArtemisMarkdownModule],
    templateUrl: './course-competency-detail-header.component.html',
})
export class CourseCompetencyDetailHeaderComponent {
    protected readonly getIcon = getIcon;
    protected readonly faPencilAlt = faPencilAlt;

    private readonly router = inject(Router);

    readonly courseId = input.required<number>();
    readonly courseCompetency = input.required<CourseCompetency>();

    readonly isMastered = input.required<boolean>();

    softDueDatePassed = computed(() => dayjs().isAfter(this.courseCompetency().softDueDate));

    navigateToEditPage() {
        // TODO: Change navigation to either prerequisite or competency
        this.router.navigate(['/course-management', this.courseId(), 'competency-management', this.courseCompetency()!.id, 'edit']);
    }
}

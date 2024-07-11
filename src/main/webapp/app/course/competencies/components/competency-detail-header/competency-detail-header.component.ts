import { Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { Competency, getIcon } from 'app/entities/competency.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import dayjs from 'dayjs';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@Component({
    selector: 'jhi-competency-detail-header',
    standalone: true,
    imports: [ArtemisSharedCommonModule, ArtemisMarkdownModule],
    templateUrl: './competency-detail-header.component.html',
})
export class CompetencyDetailHeaderComponent {
    protected readonly getIcon = getIcon;
    protected readonly faPencilAlt = faPencilAlt;

    private readonly router = inject(Router);

    readonly courseId = input.required<number>();
    readonly competency = input.required<Competency>();

    readonly isMastered = input.required<boolean>();

    softDueDatePassed = computed(() => dayjs().isAfter(this.competency().softDueDate));

    navigateToEditPage() {
        this.router.navigate(['/course-management', this.courseId(), 'competency-management', this.competency()!.id, 'edit']);
    }
}

import { Component, inject } from '@angular/core';
import { CourseCompetenciesTableDirective } from 'app/course/competencies/components/course-competencies-table/course-competencies-table.directive';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PrerequisiteApiService } from 'app/course/competencies/services/prerequisite-api.service';

@Component({
    selector: 'jhi-prerequisites-table',
    standalone: true,
    imports: [ArtemisMarkdownModule, ArtemisSharedCommonModule, ArtemisSharedModule],
    templateUrl: './prerequisites-table.component.html',
    styleUrls: ['../course-competencies-table/course-competencies-table.component.scss', './prerequisites-table.component.scss'],
})
export class PrerequisitesTableComponent extends CourseCompetenciesTableDirective<Prerequisite> {
    private readonly prerequisiteApiService = inject(PrerequisiteApiService);

    protected async deletePrerequisite(prerequisiteId: number): Promise<void> {
        await this.deleteCourseCompetency(prerequisiteId, this.prerequisiteApiService.deletePrerequisite.bind(this.prerequisiteApiService));
    }
}

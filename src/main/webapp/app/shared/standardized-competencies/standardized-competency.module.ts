import { NgModule } from '@angular/core';
import { MatTreeModule } from '@angular/material/tree';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { KnowledgeAreaTreeComponent } from 'app/shared/standardized-competencies/knowledge-area-tree.component';
import { StandardizedCompetencyFilterComponent } from 'app/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyDetailComponent } from 'app/shared/standardized-competencies/standardized-competency-detail.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedCommonModule, MatTreeModule, ArtemisMarkdownModule],
    declarations: [KnowledgeAreaTreeComponent, StandardizedCompetencyFilterComponent, StandardizedCompetencyDetailComponent],
    exports: [KnowledgeAreaTreeComponent, StandardizedCompetencyFilterComponent, StandardizedCompetencyDetailComponent],
})
export class ArtemisStandardizedCompetencyModule {}

import { NgModule } from '@angular/core';
import { MatTreeModule } from '@angular/material/tree';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { KnowledgeAreaTreeComponent } from 'app/shared/standardized-competencies/knowledge-area-tree.component';

@NgModule({
    imports: [ArtemisSharedCommonModule, MatTreeModule],
    declarations: [KnowledgeAreaTreeComponent],
    exports: [KnowledgeAreaTreeComponent],
})
export class ArtemisStandardizedCompetencyModule {}

import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FAQComponent } from 'app/faq/faq.component';
import { faqRoutes } from 'app/faq/faq.routes';
import { FAQUpdateComponent } from 'app/faq/faq-update.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
const ENTITY_STATES = [...faqRoutes];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedComponentModule,
        ArtemisMarkdownEditorModule,
        ArtemisCategorySelectorModule,
        CustomExerciseCategoryBadgeComponent,
        FAQComponent,
        FAQUpdateComponent,
    ],
    exports: [FAQComponent, FAQUpdateComponent],
})
export class ArtemisFAQModule {}

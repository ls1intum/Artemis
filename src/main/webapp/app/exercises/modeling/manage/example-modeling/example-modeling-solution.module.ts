import { NgModule } from '@angular/core';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ExampleModelingSolutionComponent } from 'app/exercises/modeling/manage/example-modeling/example-modeling-solution.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisExampleModelingSolutionRoutingModule } from 'app/exercises/modeling/manage/example-modeling/example-modeling-solution.route';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisModelingEditorModule, ArtemisExampleModelingSolutionRoutingModule, ArtemisMarkdownModule],
    declarations: [ExampleModelingSolutionComponent],
})
export class ArtemisExampleModelingSolutionModule {}

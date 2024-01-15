import { NgModule } from '@angular/core';
import { MathTaskEditorComponent } from 'app/exercises/math/editor/math-task-editor.component';
import { ExpressionEditorModule } from 'app/exercises/math/editor/expression/expression-editor.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

@NgModule({
    imports: [ExpressionEditorModule, FormsModule, ReactiveFormsModule],
    declarations: [MathTaskEditorComponent],
    exports: [MathTaskEditorComponent],
})
export class ArtemisMathTaskEditorModule {}

import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { ExpressionInputComponent } from './expression-input.component';
import { SketchInputComponent } from './sketch-input.component';
import { ImageInputComponent } from './image-input.component';
import { TextInputComponent } from './text-input.component';
import { MathOcrService } from './math-ocr.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    providers: [MathOcrService],
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, AceEditorModule, FormsModule, ReactiveFormsModule],
    declarations: [ExpressionInputComponent, SketchInputComponent, ImageInputComponent, TextInputComponent],
    exports: [ExpressionInputComponent],
})
export class ExpressionEditorModule {}

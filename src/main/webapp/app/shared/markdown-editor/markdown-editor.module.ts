import { MarkdownEditorComponent } from './markdown-editor.component';
import { NgModule } from '@angular/core';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { FormsModule } from '@angular/forms';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { SelectWithSearchComponent } from './select-with-search/select-with-search.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { DragDropModule } from '@angular/cdk/drag-drop';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule, MonacoEditorModule, FormsModule, ArtemisColorSelectorModule, MatMenuModule, MatButtonModule, DragDropModule],
    declarations: [MarkdownEditorComponent, MarkdownEditorMonacoComponent, SelectWithSearchComponent],
    exports: [MarkdownEditorComponent, MarkdownEditorMonacoComponent],
})
export class ArtemisMarkdownEditorModule {}

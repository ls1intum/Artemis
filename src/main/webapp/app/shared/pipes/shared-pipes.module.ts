import { NgModule } from '@angular/core';
import {
    HtmlForMarkdownPipe,
    KeysPipe,
    RemoveKeysPipe,
    RemovePositiveAutomaticFeedbackPipe,
    SafeHtmlPipe,
    SafeUrlPipe,
    SanitizeHtmlPipe,
    TruncatePipe,
    TypeCheckPipe,
    ExerciseTypePipe,
} from './';

@NgModule({
    declarations: [
        SafeHtmlPipe,
        SafeUrlPipe,
        RemoveKeysPipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        RemovePositiveAutomaticFeedbackPipe,
        HtmlForMarkdownPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
    ],
    exports: [
        SafeHtmlPipe,
        SafeUrlPipe,
        RemoveKeysPipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        RemovePositiveAutomaticFeedbackPipe,
        HtmlForMarkdownPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
    ],
})
export class ArtemisSharedPipesModule {}

import { NgModule } from '@angular/core';
import {
    AverageByPipe,
    ExerciseTypePipe,
    HtmlForMarkdownPipe,
    KeysPipe,
    RemoveKeysPipe,
    RemovePositiveAutomaticFeedbackPipe,
    SafeHtmlPipe,
    SafeUrlPipe,
    SanitizeHtmlPipe,
    TruncatePipe,
    TypeCheckPipe,
} from './';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { HtmlForGuidedTourMarkdownPipe } from 'app/shared';

@NgModule({
    declarations: [
        SafeHtmlPipe,
        SafeUrlPipe,
        SafeResourceUrlPipe,
        RemoveKeysPipe,
        ExerciseTypePipe,
        KeysPipe,
        TypeCheckPipe,
        RemovePositiveAutomaticFeedbackPipe,
        HtmlForMarkdownPipe,
        HtmlForGuidedTourMarkdownPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
        AverageByPipe,
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
        HtmlForGuidedTourMarkdownPipe,
        TruncatePipe,
        SanitizeHtmlPipe,
        SafeResourceUrlPipe,
        AverageByPipe,
    ],
})
export class ArtemisSharedPipesModule {}

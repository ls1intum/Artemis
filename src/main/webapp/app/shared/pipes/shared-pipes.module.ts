import { NgModule } from '@angular/core';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { HtmlForGuidedTourMarkdownPipe } from 'app/shared/pipes/html-for-guided-tour-markdown.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { TypeCheckPipe } from 'app/shared/pipes/type-check.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { AverageByPipe } from 'app/shared/pipes/average-by.pipe';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import { TruncatePipe } from 'app/shared/pipes/truncate.pipe';
import { SanitizeHtmlPipe } from 'app/shared/pipes/sanitize-html.pipe';
import { ExerciseTypePipe } from 'app/shared/pipes/exercise-type.pipe';
import { RemovePositiveAutomaticFeedbackPipe } from 'app/shared/pipes/remove-positive-automatic-feedback.pipe';
import { ExerciseCourseTitlePipe } from 'app/shared/pipes/exercise-course-title.pipe';

@NgModule({
    declarations: [
        SafeHtmlPipe,
        SafeUrlPipe,
        SafeResourceUrlPipe,
        RemoveKeysPipe,
        ExerciseCourseTitlePipe,
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
        ExerciseCourseTitlePipe,
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

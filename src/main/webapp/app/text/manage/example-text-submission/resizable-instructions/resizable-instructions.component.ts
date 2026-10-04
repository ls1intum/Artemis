import { Component, input } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';

@Component({
    selector: 'jhi-resizable-instructions',
    templateUrl: './resizable-instructions.component.html',
    styleUrls: ['./resizable-instructions.component.scss'],
    imports: [FaIconComponent, TranslateDirective, StructuredGradingInstructionsAssessmentLayoutComponent, MarkdownDirective],
})
export class ResizableInstructionsComponent {
    criteria = input.required<GradingCriterion[]>();
    problemStatement = input<string>();
    sampleSolution = input<string>();
    gradingInstructions = input<string>();
    toggleCollapse = input.required<(event: MouseEvent, type?: string) => void>();
    toggleCollapseId = input<string>();
    readOnly = input.required<boolean>();

    // Icons
    faChevronRight = faChevronRight;
    farListAlt = faListAlt;
}

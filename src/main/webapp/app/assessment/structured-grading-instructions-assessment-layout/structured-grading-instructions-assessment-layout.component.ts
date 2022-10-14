import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { AfterViewInit, Component, Input, OnInit, QueryList, ViewChildren } from '@angular/core';
import { faCompress, faExpand, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { ExpandableSectionComponent } from 'app/assessment/assessment-instructions/expandable-section/expandable-section.component';
import { delay, startWith } from 'rxjs';

@Component({
    selector: 'jhi-structured-grading-instructions-assessment-layout',
    templateUrl: './structured-grading-instructions-assessment-layout.component.html',
    styleUrls: ['./structured-grading-instructions-assessment-layout.component.scss'],
})
export class StructuredGradingInstructionsAssessmentLayoutComponent implements OnInit, AfterViewInit {
    @Input() public criteria: GradingCriterion[];
    @Input() readonly: boolean;
    allowDrop: boolean;
    // Icons
    faInfoCircle = faInfoCircle;
    faExpand = faExpand;
    faCompress = faCompress;

    @ViewChildren(ExpandableSectionComponent) expandableSections: QueryList<ExpandableSectionComponent>;
    collapseToggles: ExpandableSectionComponent[] = [];

    /**
     * OnInit set the allowDrop property to allow drop of SGI if not in readOnly mode
     */
    ngOnInit(): void {
        this.allowDrop = !this.readonly;
    }

    ngAfterViewInit() {
        this.expandableSections.changes
            .pipe(
                startWith([undefined]), // to catch the initial value
                delay(0), // wait for all current async tasks to finish, which could change the query list using ngIf etc.
            )
            .subscribe(() => {
                this.collectCollapsableSections();
            });
    }

    collapseAll() {
        this.collapseToggles.forEach((section) => {
            if (!section.isCollapsed) {
                section.toggleCollapsed();
            }
        });
    }

    expandAll() {
        this.collapseToggles.forEach((section) => {
            if (section.isCollapsed) {
                section.toggleCollapsed();
            }
        });
    }

    /**
     * Set the tooltip of the draggable grading instruction to be equal to the feedback detail text
     * @param {GradingInstruction} instr - the instruction object from which the feedback detail text is retrieved
     */
    setTooltip(instr: GradingInstruction) {
        return 'Feedback: ' + instr.feedback;
    }

    /**
     * Set the color of the draggable grading instruction based on the credits of the instruction
     *  @param {GradingInstruction} instr - the instruction object we set its color based on its credits
     */
    setInstrColour(instr: GradingInstruction) {
        let colour;
        if (instr.credits === 0) {
            colour = 'var(--sgi-assessment-layout-zero-background)';
        } else if (instr.credits < 0) {
            colour = 'var(--sgi-assessment-layout-negative-background)';
        } else {
            colour = 'var(--sgi-assessment-layout-positive-background)';
        }
        return colour;
    }
    setScore(nr: number) {
        return nr + 'P';
    }
    /**
     * Connects the SGI with the Feedback of a Submission Element in assessment detail
     * @param {Event} event - The drag event
     * @param {Object} instruction - The SGI element that should be connected with the feedback on drop
     * the corresponding drop method is in AssessmentDetailComponent
     */
    drag(event: any, instruction: GradingInstruction) {
        // The mimetype has to be text/plain to enable dragging into an external application, e.g. Orion, Apollon
        event.dataTransfer.setData('text/plain', JSON.stringify(instruction));
    }
    /**
     * disables drag if on readOnly mode
     */
    disableDrag() {
        return this.allowDrop;
    }

    collectCollapsableSections() {
        if (this.expandableSections) {
            this.collapseToggles = this.expandableSections.toArray();
        }
    }
}

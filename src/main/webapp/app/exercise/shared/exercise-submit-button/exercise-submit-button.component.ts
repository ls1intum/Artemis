import { Component, ElementRef, HostListener, computed, inject, input, output, viewChild } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { RequestFeedbackButtonComponent } from 'app/core/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';

@Component({
    selector: 'jhi-exercise-submit-button',
    templateUrl: './exercise-submit-button.component.html',
    imports: [NgbPopover, ButtonComponent, TranslateDirective, FaIconComponent, RequestFeedbackButtonComponent],
})
export class ExerciseSubmitButtonComponent {
    private readonly elementRef = inject(ElementRef);

    readonly exercise = input.required<Exercise>();
    readonly disabled = input(false);
    readonly isLoading = input(false);
    readonly title = input('entity.action.submit');
    readonly btnType = input(ButtonType.PRIMARY);
    readonly tooltip = input('');
    readonly isGeneratingFeedback = input(false);

    readonly onSubmit = output<void>();
    readonly generatingFeedback = output<void>();

    readonly popover = viewChild<NgbPopover>('popoverRef');

    readonly isAiFeedbackEnabled = computed(() => !!this.exercise().feedbackSuggestionModule);

    readonly faRobot = faRobot;

    submitAndShowPopover() {
        this.onSubmit.emit();
        this.popover()?.open();
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent) {
        const pop = this.popover();
        if (!pop?.isOpen()) {
            return;
        }

        const target = event.target as HTMLElement;

        // Don't close if clicking inside the popover, the trigger element, or an alert
        if (target.closest('.popover') || target.closest('jhi-alert-overlay') || this.elementRef.nativeElement.contains(target)) {
            return;
        }

        pop.close();
    }
}

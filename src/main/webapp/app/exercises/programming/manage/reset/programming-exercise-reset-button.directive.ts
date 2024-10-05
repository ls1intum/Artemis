import { Directive, ElementRef, HostListener, Input, OnInit, Renderer2, inject } from '@angular/core';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

@Directive({
    selector: '[jhiProgrammingExerciseResetButton]',
})
export class ProgrammingExerciseResetButtonDirective implements OnInit {
    private modalService = inject(NgbModal);
    private renderer = inject(Renderer2);
    private elementRef = inject(ElementRef);

    @Input() programmingExercise: ProgrammingExercise;

    ngOnInit() {
        this.renderer.addClass(this.elementRef.nativeElement, 'btn');
        this.renderer.addClass(this.elementRef.nativeElement, 'btn-danger');
        this.renderer.addClass(this.elementRef.nativeElement, 'btn-sm');
    }

    /**
     * Function is executed when a MouseEvent is registered. Opens the reset Dialog
     * @param event
     */
    @HostListener('click', ['$event'])
    onClick(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(ProgrammingExerciseResetDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.programmingExercise = this.programmingExercise;
    }
}

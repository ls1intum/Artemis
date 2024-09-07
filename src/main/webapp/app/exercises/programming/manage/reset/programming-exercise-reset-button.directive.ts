import { Directive, ElementRef, HostListener, Input, OnInit, Renderer2 } from '@angular/core';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

@Directive({
    selector: '[jhiProgrammingExerciseResetButton]',
})
export class ProgrammingExerciseResetButtonDirective implements OnInit {
    @Input() programmingExercise: ProgrammingExercise;

    constructor(
        private modalService: NgbModal,
        private renderer: Renderer2,
        private elementRef: ElementRef,
    ) {}

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

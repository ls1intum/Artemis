import { Directive, ElementRef, EventEmitter, HostListener, Input, OnInit, Output, Renderer2 } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { from } from 'rxjs';

import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { Exam } from 'app/entities/exam.model';
import { ExamEditWorkingTimeDialogComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time-dialog.component';
import { AlertService } from 'app/core/util/alert.service';

@Directive({ selector: '[jhiEditWorkingTimeButton]' })
export class ExamEditWorkingTimeButtonDirective implements OnInit {
    @Input() exam: Exam;
    @Output() examChange = new EventEmitter<Exam>();

    @Input() buttonSize: ButtonSize = ButtonSize.MEDIUM;
    @Input() buttonType: ButtonType = ButtonType.WARNING;

    private modalRef: NgbModalRef | null;

    constructor(
        private modalService: NgbModal,
        public alertService: AlertService,
        private renderer: Renderer2,
        private elementRef: ElementRef,
    ) {}

    /**
     * This method appends classes and type property to the button on which directive was used, additionally adds a span tag with delete text.
     * We can't use component, as Angular would wrap it in its own tag and this will break button grouping that we are using for other buttons.
     */
    ngOnInit() {
        this.renderer.addClass(this.elementRef.nativeElement, 'btn');
        this.renderer.addClass(this.elementRef.nativeElement, this.buttonType);
        this.renderer.addClass(this.elementRef.nativeElement, this.buttonSize);
        this.renderer.setProperty(this.elementRef.nativeElement, 'type', 'submit');
    }

    /**
     * Opens delete dialog
     */
    openDialog() {
        this.alertService.closeAll();
        this.modalRef = this.modalService.open(ExamEditWorkingTimeDialogComponent, { size: 'lg', backdrop: 'static', animation: true });
        this.modalRef.componentInstance.exam = this.exam;
        this.modalRef.componentInstance.examChange = (exam: Exam) => this.examChange.emit(exam);

        from(this.modalRef.result).subscribe(() => (this.modalRef = null));
    }

    /**
     * Function is executed when a MouseEvent is registered. Opens the delete Dialog
     * @param event
     */
    @HostListener('click', ['$event'])
    onClick(event: MouseEvent) {
        event.preventDefault();
        this.openDialog();
    }
}

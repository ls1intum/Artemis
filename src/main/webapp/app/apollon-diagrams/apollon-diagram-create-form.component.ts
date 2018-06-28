import { AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ApollonDiagram, ApollonDiagramService } from '../entities/apollon-diagram';

@Component({
    selector: 'jhi-apollon-diagram-create-form',
    templateUrl: './apollon-diagram-create-form.component.html',
    providers: []
})
export class ApollonDiagramCreateFormComponent implements AfterViewInit {
    apollonDiagram: ApollonDiagram;
    isSaving: boolean;
    @ViewChild('titleInput') titleInput: ElementRef;

    constructor(
        private activeModal: NgbActiveModal,
        private apollonDiagramService: ApollonDiagramService,
        private router: Router
    ) {}

    ngAfterViewInit() {
        this.titleInput.nativeElement.focus();
    }

    save() {
        this.isSaving = true;
        this.apollonDiagramService.create(this.apollonDiagram).subscribe(
            response => {
                const newDiagram = response.body as ApollonDiagram;
                this.isSaving = false;
                this.dismiss();
                this.router.navigate(['apollon-diagrams', newDiagram.id]);
            },
            () => {
                console.warn('failed');
            }
        );
    }

    dismiss() {
        this.activeModal.dismiss('cancel');
    }
}

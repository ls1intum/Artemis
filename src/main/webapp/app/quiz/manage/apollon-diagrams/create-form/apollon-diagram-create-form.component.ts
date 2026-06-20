import { AfterViewInit, Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-apollon-diagram-create-form',
    templateUrl: './apollon-diagram-create-form.component.html',
    providers: [ApollonDiagramService],
    imports: [FormsModule, TranslateDirective, FaIconComponent],
})
export class ApollonDiagramCreateFormComponent implements OnInit, AfterViewInit {
    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private apollonDiagramService = inject(ApollonDiagramService);
    private alertService = inject(AlertService);

    // Backed by a signal so the template stays reactive under zoneless change detection, while the
    // getter/setter facade keeps the [(ngModel)]="apollonDiagram.title|diagramType" deep two-way bindings working.
    private readonly _apollonDiagram = signal<ApollonDiagram>(undefined!);
    get apollonDiagram(): ApollonDiagram {
        return this._apollonDiagram();
    }
    set apollonDiagram(value: ApollonDiagram) {
        this._apollonDiagram.set(value);
    }
    readonly isSaving = signal(false);
    readonly titleInput = viewChild.required<ElementRef>('titleInput');

    // Icons
    faSave = faSave;

    ngOnInit() {
        this.apollonDiagram = this.dialogConfig.data.apollonDiagram;
    }

    /**
     * Adds focus on the title input field
     */
    ngAfterViewInit() {
        this.titleInput().nativeElement.focus();
    }

    /**
     * Saves the diagram
     */
    save() {
        this.isSaving.set(true);
        this.apollonDiagramService.create(this.apollonDiagram, this.apollonDiagram.courseId!).subscribe({
            next: ({ body }) => {
                if (body) {
                    this.isSaving.set(false);
                    this.dialogRef.close(body);
                }
            },
            error: () => {
                this.alertService.error('artemisApp.apollonDiagram.create.error');
            },
        });
    }

    /**
     * Cancels the modal
     */
    dismiss() {
        this.dialogRef.close();
    }
}

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { UMLDiagramType } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';
import { TumAetUiInputDirective } from '@tumaet/ui-angular/input';
import { TumAetUiSelectComponent } from '@tumaet/ui-angular/select';

/** The diagram types Artemis offers for Apollon drag-and-drop quiz questions. */
const DIAGRAM_TYPES: readonly UMLDiagramType[] = [
    UMLDiagramType.ClassDiagram,
    UMLDiagramType.ActivityDiagram,
    UMLDiagramType.ObjectDiagram,
    UMLDiagramType.UseCaseDiagram,
    UMLDiagramType.CommunicationDiagram,
    UMLDiagramType.ComponentDiagram,
    UMLDiagramType.DeploymentDiagram,
    UMLDiagramType.PetriNet,
    UMLDiagramType.SyntaxTree,
    UMLDiagramType.Flowchart,
    UMLDiagramType.BPMN,
];

@Component({
    selector: 'jhi-apollon-diagram-create-form',
    templateUrl: './apollon-diagram-create-form.component.html',
    styleUrls: ['./apollon-diagram-create-form.component.css'],
    providers: [ApollonDiagramService],
    imports: [FormsModule, TranslateDirective, FaIconComponent, TumAetUiButtonDirective, TumAetUiInputDirective, TumAetUiSelectComponent],
})
export class ApollonDiagramCreateFormComponent implements OnInit {
    private readonly dialogRef = inject(TumAetUiDialogRef<ApollonDiagram>);
    private readonly dialogConfig = inject(TumAetUiDialogConfig<{ apollonDiagram: ApollonDiagram }>);
    private readonly apollonDiagramService = inject(ApollonDiagramService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);

    /** Re-reads the labels when the user switches language while the dialog is open. */
    private readonly language = toSignal(this.translateService.onLangChange, { initialValue: undefined });
    readonly diagramTypeOptions = computed(() => {
        this.language();
        return DIAGRAM_TYPES.map((value) => ({ value, label: this.translateService.instant(`artemisApp.DiagramType.${value}`) }));
    });

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

    // Icons
    faSave = faSave;

    ngOnInit() {
        this.apollonDiagram = this.dialogConfig.data!.apollonDiagram;
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

import { ChangeDetectorRef, Component, ElementRef, NgZone, OnDestroy, OnInit, inject, input, output, signal, viewChild } from '@angular/core';
import { ApollonEditor, ApollonMode, ApollonView, Locale, UMLModel, importDiagram } from '@tumaet/apollon';
import { NgbModal, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { convertRenderedSVGToPNG } from '../exercise-generation/svg-renderer';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { AlertService } from 'app/shared/service/alert.service';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { TranslateService } from '@ngx-translate/core';
import { faArrowLeft, faDownload, faQuestionCircle, faX } from '@fortawesome/free-solid-svg-icons';
import { generateDragAndDropQuizExercise } from 'app/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { lastValueFrom } from 'rxjs';
import { FormsModule, NgModel } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { hasQuizRelevantElements } from 'app/modeling/shared/apollon-model.util';

@Component({
    selector: 'jhi-apollon-diagram-detail',
    templateUrl: './apollon-diagram-detail.component.html',
    providers: [ApollonDiagramService],
    imports: [TranslateDirective, FaIconComponent, FormsModule, NgbTooltip, ArtemisTranslatePipe],
})
export class ApollonDiagramDetailComponent implements OnInit, OnDestroy {
    private apollonDiagramService = inject(ApollonDiagramService);
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private modalService = inject(NgbModal);
    private elementRef = inject(ElementRef);
    private ngZone = inject(NgZone);
    private changeDetectorRef = inject(ChangeDetectorRef);

    readonly editorContainer = viewChild.required<ElementRef>('editorContainer');
    readonly titleField = viewChild<NgModel>('titleField');

    courseId = input.required<number>();
    apollonDiagramId = input.required<number>();

    closeEdit = output<DragAndDropQuestion | undefined>();
    closeModal = output<void>();

    course = signal<Course | undefined>(undefined);

    apollonDiagram = signal<ApollonDiagram | undefined>(undefined);
    apollonEditor?: ApollonEditor;
    private lastSavedModelJson = '';

    isSaved = true;

    /** Auto-save interval handle and timer counter */
    autoSaveInterval: ReturnType<typeof setInterval> | undefined;
    autoSaveTimer = 0;

    /** Whether to crop the downloaded image to the selection. */
    crop = true;

    /**
     * Whether some elements are interactive in the apollon editor.
     * v3 format: model.interactive.elements/relationships (Record<id, boolean>)
     * v4 format: model.nodes/edges are arrays - in v4 ALL elements are considered interactive
     */
    get hasInteractive(): boolean {
        return hasQuizRelevantElements(this.apollonEditor?.model);
    }

    /** Whether some elements are selected in the apollon editor. */
    get hasSelection(): boolean {
        return !!this.apollonEditor && this.apollonEditor.getSelectedElements().length > 0;
    }

    // Icons
    faDownload = faDownload;
    faQuestionCircle = faQuestionCircle;
    faArrow = faArrowLeft;
    faX = faX;

    /**
     * Initializes Apollon Editor and sets auto save timer
     */
    ngOnInit() {
        this.courseService.find(this.courseId()).subscribe({
            next: (response) => {
                this.course.set(response.body!);
            },
            error: () => {
                this.alertService.error('artemisApp.apollonDiagram.detail.error.loading');
            },
        });

        this.apollonDiagramService.find(this.apollonDiagramId(), this.courseId()).subscribe({
            next: (response) => {
                const diagram = response.body!;

                this.apollonDiagram.set(diagram);

                const model: UMLModel | undefined = diagram.jsonRepresentation ? importDiagram(JSON.parse(diagram.jsonRepresentation)) : undefined;
                this.lastSavedModelJson = model ? JSON.stringify(model) : '';
                this.initializeApollonEditor(model);
                this.setAutoSaveTimer();
            },
            error: () => {
                this.alertService.error('artemisApp.apollonDiagram.detail.error.loading');
            },
        });
    }

    /**
     * Clears auto save interval and destroys Apollon Editor
     */
    ngOnDestroy() {
        if (this.autoSaveInterval) {
            clearInterval(this.autoSaveInterval);
        }
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }
        (this.elementRef.nativeElement as any).__apollonEditor = undefined;
    }

    /**
     * Initializes Apollon Editor with UML Model
     * @param initialModel
     */
    initializeApollonEditor(initialModel?: UMLModel) {
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }

        const diagram = this.apollonDiagram();
        const editorOptions = {
            mode: ApollonMode.Modelling,
            view: ApollonView.Modelling,
            enableQuizMode: true,
            readonly: false,
            model: initialModel,
            type: diagram?.diagramType,
            locale: this.translateService.getCurrentLang() as Locale,
        } as ConstructorParameters<typeof ApollonEditor>[1] & { enableQuizMode: boolean };
        this.apollonEditor = new ApollonEditor(this.editorContainer().nativeElement, editorOptions);
        // Expose the ApollonEditor instance on the host DOM element for E2E test access.
        (this.elementRef.nativeElement as any).__apollonEditor = this.apollonEditor;
        // Wrap callback in NgZone.run() because Apollon's React/Zustand store fires outside Angular's zone.
        // Without this, programmatic model updates (e.g., from E2E tests) don't trigger change detection,
        // leaving template bindings like [disabled]="!hasInteractive" stale.
        this.apollonEditor.subscribeToModelChange((newModel) => {
            this.ngZone.run(() => {
                this.isSaved = JSON.stringify(newModel) === this.lastSavedModelJson;
                this.changeDetectorRef.markForCheck();
            });
        });
    }

    /**
     * Saves the diagram
     */
    async saveDiagram(): Promise<boolean> {
        if (!this.apollonDiagram() || !this.apollonEditor) {
            return false;
        }
        const umlModel = this.apollonEditor.model;
        const updatedDiagram = Object.assign({}, this.apollonDiagram(), {
            jsonRepresentation: JSON.stringify(umlModel),
        }) as ApollonDiagram;

        const result = await lastValueFrom(this.apollonDiagramService.update(updatedDiagram, this.courseId()));
        if (result?.ok) {
            this.alertService.success('artemisApp.apollonDiagram.updated', { title: this.apollonDiagram()?.title });
            this.lastSavedModelJson = JSON.stringify(umlModel);
            this.apollonDiagram.set(updatedDiagram);
            this.isSaved = true;
            this.setAutoSaveTimer();
            return true;
        } else {
            this.alertService.error('artemisApp.apollonDiagram.update.error');
            return false;
        }
    }

    /**
     * Closes the Detail View of an Apollon Diagram
     * If there are unsaved changes ask to confirm exit
     * @param closeModal: If the modal should be closed, or only the editor
     */
    confirmExitDetailView(closeModal: boolean) {
        if (!this.isSaved) {
            const modalRef: NgbModalRef = this.modalService.open(ConfirmAutofocusModalComponent, {
                size: 'lg',
                backdrop: 'static',
            });
            modalRef.componentInstance.title = 'artemisApp.apollonDiagram.detail.exitConfirm.title';
            modalRef.componentInstance.text = 'artemisApp.apollonDiagram.detail.exitConfirm.question';
            modalRef.componentInstance.textIsMarkdown = false;
            modalRef.componentInstance.translateText = true;
            modalRef.result.then(() => this.exitDetailView(closeModal));
        } else {
            this.exitDetailView(closeModal);
        }
    }

    exitDetailView(closeModal: boolean) {
        if (closeModal) {
            this.closeModal.emit();
        } else {
            this.closeEdit.emit(undefined);
        }
    }

    /**
     * This function sets and starts an auto-save timer that automatically saves changes
     * to the model after 30 seconds.
     */
    private setAutoSaveTimer(): void {
        if (this.autoSaveInterval) {
            clearInterval(this.autoSaveInterval);
        }
        this.autoSaveTimer = 0;
        this.autoSaveInterval = setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                this.autoSaveTimer = 0;
                this.saveDiagram();
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    /**
     * Generates the Drag and Drop Model Quiz question.
     *
     * @async
     */
    async generateExercise() {
        if (!this.hasInteractive) {
            this.alertService.error('artemisApp.apollonDiagram.create.validationError');
            return;
        }

        const diagram = this.apollonDiagram();
        const course = this.course();
        if (this.apollonEditor && diagram && course) {
            const isSaved = await this.saveDiagram();
            if (isSaved) {
                const question = await generateDragAndDropQuizExercise(course, diagram.title!, this.apollonEditor.model!);
                this.closeEdit.emit(question);
            }
        }
    }

    /**
     * Download the current selection of the diagram as a PNG image.
     *
     * @async
     */
    async downloadSelection() {
        if (!this.hasSelection || !this.apollonEditor) {
            return;
        }

        const selection = this.apollonEditor.getSelectedElements();
        const svg = await this.apollonEditor.exportAsSVG({
            keepOriginalSize: !this.crop,
            include: selection,
            svgMode: 'compat',
        });
        const png = await convertRenderedSVGToPNG(svg);
        this.download(png);
    }

    /**
     * Automatically trigger the download of a file.
     *
     * @param {Blob | File} file A `Blob` or `File` object which should be downloaded.
     */
    private download(file: Blob | File) {
        const anchor = document.createElement('a');
        document.body.appendChild(anchor);
        const url = window.URL.createObjectURL(file);
        anchor.href = url;
        anchor.download = `${this.apollonDiagram()?.title ?? 'diagram'}.png`;
        anchor.click();

        // Async revoke of ObjectURL to prevent failure on larger files.
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            document.body.removeChild(anchor);
        }, 0);
    }
}

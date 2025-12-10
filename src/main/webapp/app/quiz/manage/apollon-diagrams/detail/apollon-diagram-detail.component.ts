import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApollonEditor, ApollonMode, Locale, UMLModel } from '@ls1intum/apollon';
import { NgbModal, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
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
import { input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

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
    private languageHelper = inject(JhiLanguageHelper);
    private modalService = inject(NgbModal);
    private route = inject(ActivatedRoute);

    readonly editorContainer = viewChild.required<ElementRef>('editorContainer');
    readonly titleField = viewChild<NgModel>('titleField');

    courseId = input<number | undefined>(undefined);
    apollonDiagramId = input<number | undefined>(undefined);

    closeEdit = output<DragAndDropQuestion | undefined>();
    closeModal = output();

    private routeParams = toSignal(this.route.params, { initialValue: {} });

    resolvedCourseId = computed(() => {
        const direct = this.courseId();
        if (direct !== undefined) return direct;

        const p = this.routeParams();
        if (!('courseId' in p) || p.courseId == null || p.courseId === '') return undefined;

        const n = Number(p.courseId);
        return Number.isNaN(n) ? undefined : n;
    });

    resolvedApollonDiagramId = computed(() => {
        const direct = this.apollonDiagramId();
        if (direct !== undefined) return direct;

        const p = this.routeParams();
        if (!('id' in p) || p.id == null || p.id === '') return undefined;

        const n = Number(p.id);
        return Number.isNaN(n) ? undefined : n;
    });

    course = signal<Course>(undefined!);

    apollonDiagram = signal<ApollonDiagram>(undefined!);
    apollonEditor = signal<ApollonEditor | undefined>(undefined);

    // This is a temporary workaround until Apollon supports signals which would cause the hasInteractive signal to update automatically.
    modelChangeCounter = signal(0);

    isSaved = true;

    /**  */
    autoSaveInterval: number;
    autoSaveTimer: number;

    /** Whether to crop the downloaded image to the selection. */
    crop = true;

    /** Whether some elements are interactive in the apollon editor. */
    hasInteractive = computed(() => {
        this.modelChangeCounter();
        try {
            return (
                !!this.apollonEditor() &&
                (Object.entries(this.apollonEditor()!.model.interactive.elements).some(([, selected]) => selected) ||
                    Object.entries(this.apollonEditor()!.model.interactive.relationships).some(([, selected]) => selected))
            );
        } catch {
            return false;
        }
    });

    /** Whether some elements are selected in the apollon editor. */
    hasSelection = computed(() => {
        this.modelChangeCounter();
        try {
            return (
                !!this.apollonEditor() &&
                (Object.entries(this.apollonEditor()!.selection.elements).some(([, selected]) => selected) ||
                    Object.entries(this.apollonEditor()!.selection.relationships).some(([, selected]) => selected))
            );
        } catch {
            return false;
        }
    });

    // Icons
    faDownload = faDownload;
    faQuestionCircle = faQuestionCircle;
    faArrow = faArrowLeft;
    faX = faX;

    constructor() {
        effect(() => {
            const courseId = this.resolvedCourseId();
            const diagramId = this.resolvedApollonDiagramId();
            if (courseId === undefined || diagramId === undefined) {
                return;
            }

            this.courseService.find(courseId).subscribe({
                next: (response) => {
                    this.course.set(response.body!);
                },
                error: () => {
                    this.alertService.error('artemisApp.apollonDiagram.detail.error.loading');
                },
            });

            this.apollonDiagramService.find(diagramId, courseId).subscribe({
                next: (response) => {
                    const diagram = response.body!;

                    this.apollonDiagram.set(diagram);

                    const model: UMLModel = diagram.jsonRepresentation && JSON.parse(diagram.jsonRepresentation);
                    this.initializeApollonEditor(model);
                    this.setAutoSaveTimer();
                },
                error: () => {
                    this.alertService.error('artemisApp.apollonDiagram.detail.error.loading');
                },
            });
        });
    }

    /**
     * Initializes Apollon Editor and sets auto save timer
     */
    ngOnInit() {
        this.languageHelper.language.subscribe(async (languageKey: string) => {
            const editor = this.apollonEditor();
            if (editor) {
                await editor.nextRender;
                editor.locale = languageKey as Locale;
            }
        });
    }

    /**
     * Clears auto save interval and destroys Apollon Editor
     */
    ngOnDestroy() {
        clearInterval(this.autoSaveInterval);
        const editor = this.apollonEditor();
        if (editor) {
            editor.destroy();
        }
    }

    /**
     * Initializes Apollon Editor with UML Model
     * @param initialModel
     */
    async initializeApollonEditor(initialModel: UMLModel) {
        const currentEditor = this.apollonEditor();
        if (currentEditor) {
            currentEditor.destroy();
        }

        const newEditor = new ApollonEditor(this.editorContainer().nativeElement, {
            mode: ApollonMode.Exporting,
            model: initialModel,
            type: this.apollonDiagram()!.diagramType,
            locale: this.translateService.currentLang as Locale,
        });

        await newEditor.nextRender;

        newEditor.subscribeToModelChange((newModel) => {
            this.isSaved = JSON.stringify(newModel) === this.apollonDiagram()?.jsonRepresentation;
            this.modelChangeCounter.update((counter) => counter + 1);
        });

        this.apollonEditor.set(newEditor);
    }

    /**
     * Saves the diagram
     */
    async saveDiagram(): Promise<boolean> {
        if (!this.apollonDiagram()) {
            return false;
        }
        const umlModel = this.apollonEditor()!.model;
        const updatedDiagram: ApollonDiagram = Object.assign({}, this.apollonDiagram(), { jsonRepresentation: JSON.stringify(umlModel) });

        const result = await lastValueFrom(this.apollonDiagramService.update(updatedDiagram, this.resolvedCourseId()!));
        if (result?.ok) {
            this.alertService.success('artemisApp.apollonDiagram.updated', { title: this.apollonDiagram()?.title });
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
        this.autoSaveInterval = window.setInterval(() => {
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
        if (!this.hasInteractive()) {
            this.alertService.error('artemisApp.apollonDiagram.create.validationError');
            return;
        }

        const editor = this.apollonEditor();
        if (editor && this.apollonDiagram()) {
            const isSaved = await this.saveDiagram();
            if (isSaved) {
                const question = await generateDragAndDropQuizExercise(this.course(), this.apollonDiagram().title!, editor.model!);
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
        if (!this.hasSelection()) {
            return;
        }

        const selection = [
            ...Object.entries(this.apollonEditor()!.selection.elements)
                .filter(([, selected]) => selected)
                .map(([id]) => id),
            ...Object.entries(this.apollonEditor()!.selection.relationships)
                .filter(([, selected]) => selected)
                .map(([id]) => id),
        ];
        const svg = await this.apollonEditor()!.exportAsSVG({
            keepOriginalSize: !this.crop,
            include: selection,
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
        anchor.download = `${this.apollonDiagram()!.title}.png`;
        anchor.click();

        // Async revoke of ObjectURL to prevent failure on larger files.
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            document.body.removeChild(anchor);
        }, 0);
    }
}

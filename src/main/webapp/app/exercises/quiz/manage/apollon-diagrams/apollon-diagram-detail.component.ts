import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApollonEditor, ApollonMode, Locale, UMLModel } from '@ls1intum/apollon';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { convertRenderedSVGToPNG } from './exercise-generation/svg-renderer';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { AlertService } from 'app/core/util/alert.service';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { TranslateService } from '@ngx-translate/core';
import { faArrowLeft, faDownload, faQuestionCircle, faX } from '@fortawesome/free-solid-svg-icons';
import { generateDragAndDropQuizExercise } from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { lastValueFrom } from 'rxjs';
import { NgModel } from '@angular/forms';

@Component({
    selector: 'jhi-apollon-diagram-detail',
    templateUrl: './apollon-diagram-detail.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramDetailComponent implements OnInit, OnDestroy {
    private apollonDiagramService = inject(ApollonDiagramService);
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private languageHelper = inject(JhiLanguageHelper);
    private modalService = inject(NgbModal);
    private route = inject(ActivatedRoute);

    @ViewChild('editorContainer', { static: false }) editorContainer: ElementRef;
    @ViewChild('titleField') titleField?: NgModel;

    @Input()
    private courseId: number;
    @Input()
    private apollonDiagramId: number;

    @Output() closeEdit = new EventEmitter<DragAndDropQuestion | undefined>();
    @Output() closeModal = new EventEmitter();

    course: Course;

    apollonDiagram?: ApollonDiagram;
    apollonEditor?: ApollonEditor;

    isSaved: boolean = true;

    /**  */
    autoSaveInterval: number;
    autoSaveTimer: number;

    /** Whether to crop the downloaded image to the selection. */
    crop = true;

    /** Whether some elements are interactive in the apollon editor. */
    get hasInteractive(): boolean {
        return (
            !!this.apollonEditor &&
            (Object.entries(this.apollonEditor.model.interactive.elements).some(([, selected]) => selected) ||
                Object.entries(this.apollonEditor.model.interactive.relationships).some(([, selected]) => selected))
        );
    }

    /** Whether some elements are selected in the apollon editor. */
    get hasSelection(): boolean {
        return (
            !!this.apollonEditor &&
            (Object.entries(this.apollonEditor.selection.elements).some(([, selected]) => selected) ||
                Object.entries(this.apollonEditor.selection.relationships).some(([, selected]) => selected))
        );
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
        this.route.params.subscribe((params) => {
            this.apollonDiagramId ??= Number(params['id']);
            this.courseId ??= Number(params['courseId']);

            this.courseService.find(this.courseId).subscribe({
                next: (response) => {
                    this.course = response.body!;
                },
                error: () => {
                    this.alertService.error('artemisApp.apollonDiagram.detail.error.loading');
                },
            });

            this.apollonDiagramService.find(this.apollonDiagramId, this.courseId).subscribe({
                next: (response) => {
                    const diagram = response.body!;

                    this.apollonDiagram = diagram;

                    const model: UMLModel = diagram.jsonRepresentation && JSON.parse(diagram.jsonRepresentation);
                    this.initializeApollonEditor(model);
                    this.setAutoSaveTimer();
                },
                error: () => {
                    this.alertService.error('artemisApp.apollonDiagram.detail.error.loading');
                },
            });

            this.languageHelper.language.subscribe(async (languageKey: string) => {
                if (this.apollonEditor) {
                    await this.apollonEditor.nextRender;
                    this.apollonEditor.locale = languageKey as Locale;
                }
            });
        });
    }

    /**
     * Clears auto save interval and destroys Apollon Editor
     */
    ngOnDestroy() {
        clearInterval(this.autoSaveInterval);
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }
    }

    /**
     * Initializes Apollon Editor with UML Model
     * @param initialModel
     */
    initializeApollonEditor(initialModel: UMLModel) {
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            mode: ApollonMode.Exporting,
            model: initialModel,
            type: this.apollonDiagram!.diagramType,
            locale: this.translateService.currentLang as Locale,
        });
        this.apollonEditor.subscribeToModelChange((newModel) => {
            this.isSaved = JSON.stringify(newModel) === this.apollonDiagram?.jsonRepresentation;
        });
    }

    /**
     * Saves the diagram
     */
    async saveDiagram(): Promise<boolean> {
        if (!this.apollonDiagram) {
            return false;
        }
        const umlModel = this.apollonEditor!.model;
        const updatedDiagram: ApollonDiagram = {
            ...this.apollonDiagram,
            jsonRepresentation: JSON.stringify(umlModel),
        };

        const result = await lastValueFrom(this.apollonDiagramService.update(updatedDiagram, this.courseId));
        if (result?.ok) {
            this.alertService.success('artemisApp.apollonDiagram.updated', { title: this.apollonDiagram?.title });
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
            this.closeEdit.emit();
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
        if (!this.hasInteractive) {
            this.alertService.error('artemisApp.apollonDiagram.create.validationError');
            return;
        }

        if (this.apollonEditor && this.apollonDiagram) {
            const isSaved = await this.saveDiagram();
            if (isSaved) {
                const question = await generateDragAndDropQuizExercise(this.course, this.apollonDiagram.title!, this.apollonEditor.model!);
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
        if (!this.hasSelection) {
            return;
        }

        const selection = [
            ...Object.entries(this.apollonEditor!.selection.elements)
                .filter(([, selected]) => selected)
                .map(([id]) => id),
            ...Object.entries(this.apollonEditor!.selection.relationships)
                .filter(([, selected]) => selected)
                .map(([id]) => id),
        ];
        const svg = await this.apollonEditor!.exportAsSVG({
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
        anchor.download = `${this.apollonDiagram!.title}.png`;
        anchor.click();

        // Async revoke of ObjectURL to prevent failure on larger files.
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            document.body.removeChild(anchor);
        }, 0);
    }
}

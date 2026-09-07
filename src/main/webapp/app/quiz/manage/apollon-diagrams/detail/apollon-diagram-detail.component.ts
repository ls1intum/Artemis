import { Component, ElementRef, OnDestroy, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { ApollonEditor, ApollonMode, ApollonView, UMLModel } from '@tumaet/apollon';
import { convertRenderedSVGToPNG } from '../exercise-generation/svg-renderer';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/foundation/constants/exercise-exam-constants';
import { TranslateService } from '@ngx-translate/core';
import { faArrowLeft, faCheck, faCropSimple, faDownload, faFloppyDisk, faPen, faXmark } from '@fortawesome/free-solid-svg-icons';
import { generateDragAndDropQuizExercise } from 'app/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ConfirmAutofocusModalResult, openConfirmAutofocusDialog } from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { lastValueFrom } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ApollonModelData, hasQuizRelevantElements, normalizeApollonModel } from 'app/modeling/shared/apollon-model.util';
import { DialogService } from 'primeng/dynamicdialog';
import { parseJson } from 'app/foundation/util/json.util';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { createApollonLabels } from 'app/modeling/shared/modeling-editor/apollon-labels';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiButtonDirective, TumUiInputDirective, TumUiTagComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';

type ApollonEditorHostElement = HTMLElement & { __apollonEditor?: ApollonEditor };

@Component({
    selector: 'jhi-apollon-diagram-detail',
    templateUrl: './apollon-diagram-detail.component.html',
    styleUrls: ['./apollon-diagram-detail.component.scss'],
    providers: [ApollonDiagramService],
    imports: [TranslateDirective, FaIconComponent, FormsModule, ArtemisTranslatePipe, TumUiButtonDirective, TumUiInputDirective, TumUiTagComponent, TumUiTooltipDirective],
})
export class ApollonDiagramDetailComponent implements OnInit, OnDestroy {
    private apollonDiagramService = inject(ApollonDiagramService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private dialogService = inject(DialogService);
    private elementRef = inject(ElementRef);

    readonly editorContainer = viewChild.required<ElementRef>('editorContainer');
    private readonly editorActions = viewChild<ElementRef<HTMLElement>>('editorActions');

    courseId = input.required<number>();
    apollonDiagramId = input.required<number>();

    closeEdit = output<DragAndDropQuestion | undefined>();
    closeModal = output<void>();

    apollonDiagram = signal<ApollonDiagram | undefined>(undefined);
    apollonEditor?: ApollonEditor;
    private readonly lastSavedModelJson = signal('');
    private readonly currentModelJson = signal('');
    private modelSubscription: number | undefined;
    private selectionSubscription: number | undefined;
    private actionsRegionMounted = false;

    readonly title = signal('');
    readonly isTitleValid = computed(() => this.title().trim().length > 0);

    readonly isSaved = computed(() => this.currentModelJson() === this.lastSavedModelJson() && this.title() === (this.apollonDiagram()?.title ?? ''));

    readonly cropToSelection = signal(true);

    readonly selectedElementIds = signal<string[]>([]);
    readonly hasSelection = computed(() => this.selectedElementIds().length > 0);

    /** Apollon's model lives in its own store, so it is mirrored here for anything derived from it. */
    private readonly currentModel = signal<UMLModel | undefined>(undefined);

    readonly hasInteractive = computed(() => hasQuizRelevantElements(this.currentModel()));

    readonly downloadHint = computed(() =>
        this.translateService.instant(this.hasSelection() ? 'artemisApp.apollonDiagram.detail.help' : 'artemisApp.apollonDiagram.detail.downloadHint'),
    );
    readonly canGenerate = computed(() => !!this.apollonDiagram() && this.isTitleValid() && this.hasInteractive());
    readonly generateHint = computed(() => (this.hasInteractive() ? '' : this.translateService.instant('artemisApp.apollonDiagram.create.validationError')));

    autoSaveInterval: ReturnType<typeof setInterval> | undefined;
    autoSaveTimer = 0;

    faDownload = faDownload;
    faCropSimple = faCropSimple;
    faArrowLeft = faArrowLeft;
    faXmark = faXmark;
    faFloppyDisk = faFloppyDisk;
    faCheck = faCheck;
    faPen = faPen;

    constructor() {
        this.translateService.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
            this.apollonEditor?.setLabels(createApollonLabels(this.translateService));
        });
    }

    ngOnInit() {
        this.apollonDiagramService.find(this.apollonDiagramId(), this.courseId()).subscribe({
            next: (response) => {
                const diagram = response.body!;

                this.apollonDiagram.set(diagram);
                this.title.set(diagram.title ?? '');

                const model = diagram.jsonRepresentation ? parseJson<ApollonModelData>(diagram.jsonRepresentation) : undefined;
                this.initializeApollonEditor(model);
                this.setAutoSaveTimer();
            },
            error: () => {
                this.alertService.error('artemisApp.apollonDiagram.detail.error.loading');
            },
        });
    }

    ngOnDestroy() {
        if (this.autoSaveInterval) {
            clearInterval(this.autoSaveInterval);
            this.autoSaveInterval = undefined;
        }
        this.destroyApollonEditor();
    }

    initializeApollonEditor(initialModel?: UMLModel | ApollonModelData) {
        this.destroyApollonEditor();

        const diagram = this.apollonDiagram();
        const normalizedModel = initialModel ? normalizeApollonModel(initialModel) : undefined;
        const savedModelJson = normalizedModel ? JSON.stringify(normalizedModel) : '';
        this.lastSavedModelJson.set(savedModelJson);
        this.currentModelJson.set(savedModelJson);
        const editorOptions: ConstructorParameters<typeof ApollonEditor>[1] = {
            mode: ApollonMode.Modelling,
            view: ApollonView.Modelling,
            readonly: false,
            model: normalizedModel,
            type: diagram?.diagramType,
            labels: createApollonLabels(this.translateService),
            availableViews: [ApollonView.Modelling, ApollonView.Highlight],
        };
        this.apollonEditor = new ApollonEditor(this.editorContainer().nativeElement, editorOptions);
        (this.elementRef.nativeElement as ApollonEditorHostElement).__apollonEditor = this.apollonEditor;
        // Apollon's React/Zustand store fires outside Angular; the signal writes below schedule
        // change detection under zoneless, so template bindings stay fresh.
        this.modelSubscription = this.apollonEditor.subscribeToModelChange((newModel) => {
            this.currentModelJson.set(JSON.stringify(newModel));
            this.currentModel.set(newModel);
        });
        this.selectionSubscription = this.apollonEditor.subscribeToSelectionChange((selectedElementIds) => {
            this.selectedElementIds.set(selectedElementIds);
        });
        this.selectedElementIds.set(this.apollonEditor.getSelectedElements());
        this.currentModel.set(this.apollonEditor.model);
        this.mountEditorActions();
    }

    /**
     * Hands the canvas-scoped controls to Apollon's top-right overlay region so they sit inside the
     * canvas next to the palette, zoom and minimap instead of stacking another toolbar above it.
     */
    private mountEditorActions(): void {
        const actions = this.editorActions()?.nativeElement;
        if (!actions || !this.apollonEditor || this.actionsRegionMounted) {
            return;
        }
        this.apollonEditor.getRegionElement('top-right').append(actions);
        this.actionsRegionMounted = true;
    }

    private destroyApollonEditor(): void {
        const editor = this.apollonEditor;
        this.apollonEditor = undefined;
        (this.elementRef.nativeElement as ApollonEditorHostElement).__apollonEditor = undefined;
        if (editor) {
            if (this.modelSubscription !== undefined) {
                editor.unsubscribe(this.modelSubscription);
                this.modelSubscription = undefined;
            }
            if (this.selectionSubscription !== undefined) {
                editor.unsubscribe(this.selectionSubscription);
                this.selectionSubscription = undefined;
            }
            if (this.actionsRegionMounted) {
                editor.releaseRegionElement('top-right');
                this.actionsRegionMounted = false;
            }
        }
        this.selectedElementIds.set([]);
        editor?.destroy();
    }

    async saveDiagram(): Promise<boolean> {
        if (!this.apollonDiagram() || !this.apollonEditor) {
            return false;
        }
        const umlModel = this.apollonEditor.model;
        const updatedDiagram = deepClone(this.apollonDiagram()!);
        updatedDiagram.jsonRepresentation = JSON.stringify(umlModel);
        updatedDiagram.title = this.title().trim() || updatedDiagram.title;

        const result = await lastValueFrom(this.apollonDiagramService.update(updatedDiagram, this.courseId()));
        if (result?.ok) {
            this.alertService.success('artemisApp.apollonDiagram.updated', { title: updatedDiagram.title });
            const savedModelJson = JSON.stringify(umlModel);
            this.lastSavedModelJson.set(savedModelJson);
            this.currentModelJson.set(savedModelJson);
            this.apollonDiagram.set(updatedDiagram);
            this.title.set(updatedDiagram.title ?? '');
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
        if (!this.isSaved()) {
            const dialogRef = openConfirmAutofocusDialog(this.dialogService, {
                title: 'artemisApp.apollonDiagram.detail.exitConfirm.title',
                text: 'artemisApp.apollonDiagram.detail.exitConfirm.question',
                translateText: true,
            });
            dialogRef?.onClose.subscribe((result: ConfirmAutofocusModalResult | undefined) => {
                if (result?.confirmed) {
                    this.exitDetailView(closeModal);
                }
            });
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

    private setAutoSaveTimer(): void {
        if (this.autoSaveInterval) {
            clearInterval(this.autoSaveInterval);
        }
        this.autoSaveTimer = 0;
        this.autoSaveInterval = setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                this.autoSaveTimer = 0;
                void this.saveDiagram();
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    async generateExercise() {
        if (!this.hasInteractive()) {
            this.alertService.error('artemisApp.apollonDiagram.create.validationError');
            return;
        }

        const diagram = this.apollonDiagram();
        if (this.apollonEditor && diagram) {
            const isSaved = await this.saveDiagram();
            if (isSaved) {
                const question = await generateDragAndDropQuizExercise(this.apollonDiagram()!.title!, this.apollonEditor.model);
                this.closeEdit.emit(question);
            }
        }
    }

    async downloadSelection() {
        if (!this.hasSelection() || !this.apollonEditor) {
            return;
        }

        const svg = await this.apollonEditor.exportAsSVG({
            keepOriginalSize: !this.cropToSelection(),
            include: this.selectedElementIds(),
            svgMode: 'compat',
        });
        const png = await convertRenderedSVGToPNG(svg);
        this.download(png);
    }

    private download(file: Blob | File) {
        const anchor = document.createElement('a');
        document.body.appendChild(anchor);
        const url = window.URL.createObjectURL(file);
        anchor.href = url;
        anchor.download = `${this.title().trim() || this.apollonDiagram()?.title || 'diagram'}.png`;
        anchor.click();

        // Async revoke of ObjectURL to prevent failure on larger files.
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            document.body.removeChild(anchor);
        }, 0);
    }
}

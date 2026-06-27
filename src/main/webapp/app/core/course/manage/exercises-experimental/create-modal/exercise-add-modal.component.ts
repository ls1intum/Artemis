import { Component, Type, effect, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faArrowRight, faCheckDouble, faFileUpload, faFont, faKeyboard, faLayerGroup, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportComponent, ExerciseImportDialogData } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { IMPORT_DIALOG_BACK, ImportDialogFooterComponent } from 'app/core/course/manage/exercises-experimental/create-modal/import-dialog-footer.component';
import { DialogTranslateHeaderComponent } from 'app/shared-ui/dynamic-dialog/dialog-translate-header.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export type AddModalMode = 'create' | 'import' | 'export' | 'unified';

interface ExerciseTypeCard {
    type: ExerciseType;
    labelKey: string;
    descriptionKey: string;
    icon: typeof faKeyboard;
    accentClass: string;
    routeSegment: string;
}

const EXERCISE_TYPE_CARDS: ExerciseTypeCard[] = [
    {
        type: ExerciseType.PROGRAMMING,
        labelKey: 'artemisApp.exerciseManagement.type.PROGRAMMING',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.PROGRAMMING',
        icon: faKeyboard,
        accentClass: 'card--programming',
        routeSegment: 'programming-exercises/new',
    },
    {
        type: ExerciseType.QUIZ,
        labelKey: 'artemisApp.exerciseManagement.type.QUIZ',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.QUIZ',
        icon: faCheckDouble,
        accentClass: 'card--quiz',
        routeSegment: 'quiz-exercises/new',
    },
    {
        type: ExerciseType.MODELING,
        labelKey: 'artemisApp.exerciseManagement.type.MODELING',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.MODELING',
        icon: faProjectDiagram,
        accentClass: 'card--modeling',
        routeSegment: 'modeling-exercises/new',
    },
    {
        type: ExerciseType.TEXT,
        labelKey: 'artemisApp.exerciseManagement.type.TEXT',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.TEXT',
        icon: faFont,
        accentClass: 'card--text',
        routeSegment: 'text-exercises/new',
    },
    {
        type: ExerciseType.FILE_UPLOAD,
        labelKey: 'artemisApp.exerciseManagement.type.FILE_UPLOAD',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.FILE_UPLOAD',
        icon: faFileUpload,
        accentClass: 'card--fileupload',
        routeSegment: 'file-upload-exercises/new',
    },
];

@Component({
    selector: 'jhi-exercise-add-modal',
    templateUrl: './exercise-add-modal.component.html',
    styleUrl: './exercise-add-modal.component.scss',
    imports: [DialogModule, ButtonModule, FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
})
export class ExerciseAddModalComponent {
    readonly visible = input<boolean>(false);
    readonly mode = input<AddModalMode>('create');
    readonly courseId = input<number | undefined>(undefined);

    readonly visibleChange = output<boolean>();
    readonly groupCreate = output<void>();
    readonly exportRequested = output<void>();

    protected readonly exerciseTypeCards = EXERCISE_TYPE_CARDS;

    readonly activeTab = signal<'create' | 'import' | 'export'>('create');

    protected readonly faArrowRight = faArrowRight;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faLayerGroup = faLayerGroup;

    private readonly router = inject(Router);
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    constructor() {
        effect(() => {
            if (this.visible()) {
                const m = this.mode();
                if (m === 'create' || m === 'import' || m === 'export') {
                    this.setActiveTab(m);
                }
            }
        });
    }

    get dialogHeader(): string {
        return this.translateService.instant('artemisApp.exerciseManagement.addModal.header');
    }

    close(): void {
        this.visibleChange.emit(false);
    }

    navigateToCreate(card: ExerciseTypeCard): void {
        const id = this.courseId();
        if (id !== undefined) {
            this.router.navigate(['/course-management', id, ...card.routeSegment.split('/')]);
        }
        this.close();
    }

    setActiveTab(tab: 'create' | 'import' | 'export'): void {
        this.activeTab.set(tab);
    }

    /**
     * Opens the regular Artemis exercise import dialog (the one used on develop) for the selected exercise type.
     * Mirrors {@link ExerciseImportButtonComponent}: programming exercises use the tabbed variant (which also
     * allows importing from a file), all other types use the direct import list. When mock data is enabled the
     * dialog is populated from the mock catalogue via {@link MockCourseInterceptor}; otherwise it shows the real
     * exercises returned by the paging endpoints.
     */
    startImport(type: ExerciseType): void {
        this.close();

        const headerKey = type === ExerciseType.FILE_UPLOAD ? 'artemisApp.fileUploadExercise.home.importLabel' : `artemisApp.${type}Exercise.home.importLabel`;
        const dialogData: ExerciseImportDialogData & { headerKey: string } = { exerciseType: type, headerKey };
        const componentToOpen: Type<any> = type === ExerciseType.PROGRAMMING ? ExerciseImportTabsComponent : ExerciseImportComponent;

        const dialogRef = this.dialogService.open(componentToOpen, {
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: dialogData,
            // Reactive title (re-translates on a language switch) and a "Back" button in the footer that returns to this
            // modal's exercise-type selection (see onClose). PrimeNG's static `header` string would not re-translate.
            templates: { header: DialogTranslateHeaderComponent, footer: ImportDialogFooterComponent },
        });

        dialogRef?.onClose.subscribe((result: Exercise | string | undefined) => {
            if (result === IMPORT_DIALOG_BACK) {
                this.reopenOnImportTab();
            } else if (result) {
                const exercise = result as Exercise;
                if (type === ExerciseType.PROGRAMMING) {
                    this.handleProgrammingImport(exercise);
                } else {
                    this.router.navigate(['/course-management', this.courseId(), type + '-exercises', exercise.id, 'import']);
                }
            }
        });
    }

    /** Reopens this modal at the import-type selection after the user pressed "Back" in the import dialog. */
    private reopenOnImportTab(): void {
        this.setActiveTab('import');
        this.visibleChange.emit(true);
    }

    private handleProgrammingImport(result: Exercise): void {
        // When the exercise is imported from a file, the id is undefined.
        if (result.id === undefined) {
            this.router.navigate(['/course-management', this.courseId(), 'programming-exercises', 'import-from-file'], {
                state: { programmingExerciseForImportFromFile: result },
            });
        } else {
            this.router.navigate(['/course-management', this.courseId(), 'programming-exercises', 'import', result.id]);
        }
    }

    /**
     * Asks the host to open the quiz export dialog (the develop quiz export page shown as a modal component). Quiz
     * exercises are currently the only exercise type that supports export. With mock data enabled the dialog is
     * populated from the mock quiz catalogue via {@link MockCourseInterceptor}.
     */
    requestExport(): void {
        this.exportRequested.emit();
        this.close();
    }

    createGroup(): void {
        this.groupCreate.emit();
        this.close();
    }
}

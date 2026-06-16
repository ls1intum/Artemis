import { Component, Type, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faArrowRight, faCode, faFileAlt, faFileUpload, faLayerGroup, faPencilAlt, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportComponent, ExerciseImportDialogData } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { IMPORT_DIALOG_BACK, ImportDialogFooterComponent } from 'app/core/course/manage/exercises-experimental/create-modal/import-dialog-footer.component';

export type AddModalMode = 'create' | 'import' | 'export' | 'unified';

interface ExerciseTypeCard {
    type: ExerciseType;
    label: string;
    description: string;
    icon: typeof faCode;
    accentClass: string;
    routeSegment: string;
}

interface MockImportGroup {
    id: number;
    title: string;
    course: string;
    exerciseCount: number;
}

const MOCK_IMPORT_GROUPS: MockImportGroup[] = [
    { id: 601, title: 'Loops', course: 'Intro to CS (WS 23/24)', exerciseCount: 3 },
    { id: 602, title: 'Arrays and Lists', course: 'Intro to CS (WS 23/24)', exerciseCount: 2 },
    { id: 603, title: 'OOP Fundamentals', course: 'Software Engineering (SS 24)', exerciseCount: 4 },
    { id: 604, title: 'Sorting Algorithms', course: 'Algorithms (WS 24/25)', exerciseCount: 3 },
];

const EXERCISE_TYPE_CARDS: ExerciseTypeCard[] = [
    {
        type: ExerciseType.PROGRAMMING,
        label: 'Programming',
        description: 'Automated grading with test suites. Supports Java, Python, C, and more.',
        icon: faCode,
        accentClass: 'card--programming',
        routeSegment: 'programming-exercises/new',
    },
    {
        type: ExerciseType.QUIZ,
        label: 'Quiz',
        description: 'Multiple choice, short answer, and drag-and-drop questions.',
        icon: faQuestion,
        accentClass: 'card--quiz',
        routeSegment: 'quiz-exercises/new',
    },
    {
        type: ExerciseType.MODELING,
        label: 'Modeling',
        description: 'UML diagrams and model-based exercises with semi-automatic assessment.',
        icon: faPencilAlt,
        accentClass: 'card--modeling',
        routeSegment: 'modeling-exercises/new',
    },
    {
        type: ExerciseType.TEXT,
        label: 'Text',
        description: 'Free-text essays and open-ended questions with manual review.',
        icon: faFileAlt,
        accentClass: 'card--text',
        routeSegment: 'text-exercises/new',
    },
    {
        type: ExerciseType.FILE_UPLOAD,
        label: 'File Upload',
        description: 'Worksheet or document submissions reviewed by instructors.',
        icon: faFileUpload,
        accentClass: 'card--fileupload',
        routeSegment: 'file-upload-exercises/new',
    },
];

@Component({
    selector: 'jhi-exercise-add-modal',
    templateUrl: './exercise-add-modal.component.html',
    styleUrl: './exercise-add-modal.component.scss',
    imports: [DialogModule, ButtonModule, CheckboxModule, FormsModule, FaIconComponent],
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
    readonly importSelectedType = signal<string | null>(null);
    readonly importGroupSelectedIds = signal<Set<number>>(new Set());

    protected readonly faArrowRight = faArrowRight;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faLayerGroup = faLayerGroup;

    private readonly router = inject(Router);
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    constructor() {
        effect(() => {
            const m = this.mode();
            if (m === 'create' || m === 'import' || m === 'export') {
                this.setActiveTab(m);
            }
        });
    }

    get dialogHeader(): string {
        return 'Manage Exercises';
    }

    close(): void {
        this.visibleChange.emit(false);
    }

    navigateToCreate(card: ExerciseTypeCard): void {
        const id = this.courseId();
        if (id !== undefined) {
            this.router.navigate(['/course-management', id, card.routeSegment]);
        }
        this.close();
    }

    setActiveTab(tab: 'create' | 'import' | 'export'): void {
        this.activeTab.set(tab);
        this.importSelectedType.set(null);
        this.importGroupSelectedIds.set(new Set());
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
        // Wait for this modal's close animation to finish before opening the import dialog, so the two transitions
        // are sequential rather than an abrupt swap (kept consistent with the export flow).
        setTimeout(() => this.openImportDialog(type), ExerciseAddModalComponent.MODAL_CLOSE_ANIMATION_MS);
    }

    private openImportDialog(type: ExerciseType): void {
        const dialogData: ExerciseImportDialogData = { exerciseType: type };
        const headerKey = type === ExerciseType.FILE_UPLOAD ? 'artemisApp.fileUploadExercise.home.importLabel' : `artemisApp.${type}Exercise.home.importLabel`;
        const componentToOpen: Type<any> = type === ExerciseType.PROGRAMMING ? ExerciseImportTabsComponent : ExerciseImportComponent;

        const dialogRef = this.dialogService.open(componentToOpen, {
            header: this.translateService.instant(headerKey),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: dialogData,
            // A "Back" button in the dialog footer returns to this modal's exercise-type selection (see onClose).
            templates: { footer: ImportDialogFooterComponent },
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

    selectImportType(type: string): void {
        this.importSelectedType.set(type);
        this.importGroupSelectedIds.set(new Set());
    }

    backToImportTypeSelection(): void {
        this.importSelectedType.set(null);
        this.importGroupSelectedIds.set(new Set());
    }

    importGroups(): MockImportGroup[] {
        return MOCK_IMPORT_GROUPS;
    }

    toggleImportGroupSelection(id: number): void {
        const next = new Set(this.importGroupSelectedIds());
        if (next.has(id)) next.delete(id);
        else next.add(id);
        this.importGroupSelectedIds.set(next);
    }

    confirmImport(): void {
        this.close();
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

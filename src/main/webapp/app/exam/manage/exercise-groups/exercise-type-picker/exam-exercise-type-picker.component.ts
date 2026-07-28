import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faArrowRight, faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

export type ExamExerciseTypePickerMode = 'create' | 'import';

interface ExerciseTypeCard {
    type: ExerciseType;
    labelKey: string;
    descriptionKey: string;
    icon: IconProp;
    accentClass: string;
}

const EXERCISE_TYPE_CARDS: ExerciseTypeCard[] = [
    {
        type: ExerciseType.PROGRAMMING,
        labelKey: 'artemisApp.exerciseManagement.type.PROGRAMMING',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.PROGRAMMING',
        icon: faKeyboard,
        accentClass: 'card--programming',
    },
    {
        type: ExerciseType.QUIZ,
        labelKey: 'artemisApp.exerciseManagement.type.QUIZ',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.QUIZ',
        icon: faCheckDouble,
        accentClass: 'card--quiz',
    },
    {
        type: ExerciseType.MODELING,
        labelKey: 'artemisApp.exerciseManagement.type.MODELING',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.MODELING',
        icon: faProjectDiagram,
        accentClass: 'card--modeling',
    },
    {
        type: ExerciseType.TEXT,
        labelKey: 'artemisApp.exerciseManagement.type.TEXT',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.TEXT',
        icon: faFont,
        accentClass: 'card--text',
    },
    {
        type: ExerciseType.FILE_UPLOAD,
        labelKey: 'artemisApp.exerciseManagement.type.FILE_UPLOAD',
        descriptionKey: 'artemisApp.exerciseManagement.addModal.cardDescription.FILE_UPLOAD',
        icon: faFileUpload,
        accentClass: 'card--fileupload',
    },
];

/**
 * Per-group exercise-type picker for the exam exercise-groups page, styled after the course-side
 * {@code ExerciseAddModalComponent} — same type-card grid AND the same create/import tab bar, so both "add
 * exercise" flows look and behave identically. The host's "Add Exercise" / "Import Exercise" buttons only pick
 * which tab the dialog *opens* on (via {@link mode}); the tab bar lets the user switch without closing the dialog,
 * matching the course-side modal exactly.
 *
 * `create` navigates to the type's exam create route for {@link groupId}; `import` delegates to the host, which
 * already owns the import dialog wiring via {@code ExerciseGroupsComponent#openImportModal}.
 */
@Component({
    selector: 'jhi-exam-exercise-type-picker',
    templateUrl: './exam-exercise-type-picker.component.html',
    styleUrl: './exam-exercise-type-picker.component.scss',
    imports: [TumUiDialogComponent, FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExamExerciseTypePickerComponent {
    private readonly router = inject(Router);

    readonly visible = model<boolean>(false);
    /** Which tab the dialog opens on; re-applied every time it opens (see the constructor effect). */
    readonly mode = input<ExamExerciseTypePickerMode>('create');
    readonly courseId = input.required<number>();
    readonly examId = input.required<number>();
    readonly groupId = input.required<number>();
    /** Exercise types hidden from the picker because their module feature / toggle is inactive. */
    readonly disabledExerciseTypes = input<ExerciseType[]>([]);

    /** Emitted instead of navigating on the import tab, since the import dialog itself is owned by the host page. */
    readonly importRequested = output<ExerciseType>();

    protected readonly faArrowRight = faArrowRight;

    /** The tab currently shown; switchable in-dialog independently of {@link mode}. */
    protected readonly activeTab = signal<ExamExerciseTypePickerMode>('create');

    protected readonly typeCards = computed(() => EXERCISE_TYPE_CARDS.filter((card) => !this.disabledExerciseTypes().includes(card.type)));

    constructor() {
        // Re-apply the opener's chosen tab every time the dialog opens, exactly like ExerciseAddModalComponent —
        // otherwise re-opening via "Import Exercise" after a prior "Add Exercise" session would still show Import's
        // last-left tab.
        effect(() => {
            if (this.visible()) {
                this.activeTab.set(this.mode());
            }
        });
    }

    protected setActiveTab(tab: ExamExerciseTypePickerMode): void {
        this.activeTab.set(tab);
    }

    protected onCardClick(type: ExerciseType): void {
        if (this.activeTab() === 'import') {
            this.importRequested.emit(type);
        } else {
            void this.router.navigate(['/course-management', this.courseId(), 'exams', this.examId(), 'exercise-groups', this.groupId(), `${type}-exercises`, 'new']);
        }
        this.visible.set(false);
    }
}

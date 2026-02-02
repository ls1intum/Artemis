import { Component, computed, inject, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { catchError, firstValueFrom, of } from 'rxjs';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { ExerciseMetadataConflictDiffComponent } from 'app/exercise/synchronization/exercise-metadata-conflict-diff.component';

/**
 * Single field conflict between current editor state and incoming snapshot.
 */
export interface ExerciseMetadataConflictItem {
    field: string;
    labelKey: string;
    currentValue: unknown;
    incomingValue: unknown;
}

/**
 * Resolution decision for a single conflicting field.
 */
export interface ExerciseMetadataConflictDecision {
    field: string;
    useIncoming: boolean;
    resolvedValue?: unknown;
}

/**
 * Result payload returned by the conflict resolution modal.
 */
export interface ExerciseMetadataConflictModalResult {
    decisions: ExerciseMetadataConflictDecision[];
}

@Component({
    selector: 'jhi-exercise-metadata-conflict-modal',
    templateUrl: './exercise-metadata-conflict-modal.component.html',
    styleUrls: ['./exercise-metadata-conflict-modal.component.scss'],
    imports: [FormsModule, TranslateDirective, FaIconComponent, ExerciseMetadataConflictDiffComponent],
})
export class ExerciseMetadataConflictModalComponent {
    activeModal = inject(NgbActiveModal);
    private programmingExerciseGradingService = inject(ProgrammingExerciseGradingService);

    readonly conflicts = signal<ExerciseMetadataConflictItem[]>([]);
    readonly author = signal<UserPublicInfoDTO | undefined>(undefined);
    readonly versionId = signal<number | undefined>(undefined);
    readonly decisions = signal<Record<string, boolean>>({});
    readonly resolvedTexts = signal<Record<string, string>>({});
    readonly resolvedInitialized = signal<Record<string, boolean>>({});
    readonly resolvedTouched = signal<Record<string, boolean>>({});
    readonly exerciseId = signal<number | undefined>(undefined);
    readonly exerciseType = signal<ExerciseType | undefined>(undefined);

    private testCaseNameById = new Map<number, string>();
    private testCaseIdByName = new Map<string, number>();
    private testCasesLoaded = false;
    private testCasesLoading = false;
    private readonly testCasesVersion = signal(0);

    readonly authorName = computed(() => {
        const author = this.author();
        if (!author) {
            return '';
        }
        const fullName = [author.firstName, author.lastName].filter(Boolean).join(' ');
        return author.name ?? (fullName || author.login || '');
    });

    readonly faExclamationTriangle = faExclamationTriangle;

    /**
     * Initializes conflicts and decision state for the modal.
     */
    setConflicts(conflicts: ExerciseMetadataConflictItem[]): void {
        this.conflicts.set(conflicts);
        const nextDecisions: Record<string, boolean> = {};
        for (const conflict of conflicts) {
            nextDecisions[conflict.field] = false;
        }
        this.decisions.set(nextDecisions);
        this.maybeLoadTestCases();
    }

    /**
     * Sets the author of the incoming exercise version.
     */
    setAuthor(author: UserPublicInfoDTO): void {
        this.author.set(author);
    }

    /**
     * Sets the incoming exercise version id.
     */
    setVersionId(versionId: number): void {
        this.versionId.set(versionId);
    }

    /**
     * Sets the current exercise id and triggers any required metadata loading.
     */
    setExerciseId(exerciseId: number): void {
        this.exerciseId.set(exerciseId);
        this.maybeLoadTestCases();
    }

    /**
     * Sets the current exercise type and triggers any required metadata loading.
     */
    setExerciseType(exerciseType: ExerciseType): void {
        this.exerciseType.set(exerciseType);
        this.maybeLoadTestCases();
    }

    /**
     * Updates the checkbox decision state for a single field.
     */
    updateDecision(field: string, value: boolean): void {
        const updated = Object.assign({}, this.decisions());
        updated[field] = value;
        this.decisions.set(updated);
    }

    /**
     * Emits the chosen decisions and closes the modal.
     */
    applySelections(): void {
        const decisions = this.conflicts().map((conflict) => ({
            field: conflict.field,
            useIncoming: Boolean(this.decisions()[conflict.field]),
            resolvedValue: this.getResolvedValue(conflict),
        }));
        this.activeModal.close({ decisions } satisfies ExerciseMetadataConflictModalResult);
    }

    /**
     * Emits decisions that keep all local values and closes the modal.
     */
    keepLocalChanges(): void {
        const decisions = this.conflicts().map((conflict) => ({
            field: conflict.field,
            useIncoming: false,
            resolvedValue: this.getResolvedValue(conflict, false),
        }));
        this.activeModal.close({ decisions } satisfies ExerciseMetadataConflictModalResult);
    }

    /**
     * Dismisses the modal without applying changes.
     */
    close(): void {
        this.activeModal.dismiss();
    }

    /**
     * Formats values for display in the conflict table.
     */
    formatValue(value: unknown): string {
        if (value === undefined || value === null) {
            return '—';
        }
        if (dayjs.isDayjs(value)) {
            return value.format('YYYY-MM-DD HH:mm');
        }
        if (typeof value === 'string') {
            return value;
        }
        if (typeof value === 'number' || typeof value === 'boolean') {
            return String(value);
        }
        try {
            return JSON.stringify(value);
        } catch {
            return String(value);
        }
    }

    /**
     * Returns true when the field is the problem statement.
     */
    isProblemStatementField(field: string): boolean {
        return field === 'problemStatement';
    }

    /**
     * Returns true when the conflict field should be resolved with a diff editor.
     */
    isEditableDiffField(field: string): boolean {
        return (
            field === 'problemStatement' ||
            field === 'textData.exampleSolution' ||
            field === 'fileUploadData.exampleSolution' ||
            field === 'modelingData.exampleSolutionExplanation' ||
            field === 'modelingData.exampleSolutionModel'
        );
    }

    /**
     * Returns true when the conflict field represents quiz questions.
     */
    isQuizQuestionsField(field: string): boolean {
        return field === 'quizData.quizQuestions';
    }

    /**
     * Returns true when the conflict field represents build configuration.
     */
    isBuildConfigField(field: string): boolean {
        return field === 'programmingData.buildConfig';
    }

    /**
     * Returns true when the conflict field represents categories.
     */
    isCategoriesField(field: string): boolean {
        return field === 'categories';
    }

    /**
     * Returns true when the conflict field represents competency links.
     */
    isCompetencyLinksField(field: string): boolean {
        return field === 'competencyLinks';
    }

    /**
     * Builds a stable diff file base name for the Monaco editor.
     */
    getDiffFileBase(field: string): string {
        return `conflict-${field.split('.').join('-')}`;
    }

    /**
     * Maps build config snapshots into a grid-friendly list of entries.
     */
    getBuildConfigEntries(currentValue: unknown, incomingValue: unknown): Array<{ labelKey: string; current: unknown; incoming: unknown }> {
        const current = (currentValue as ProgrammingExerciseBuildConfig | undefined) ?? undefined;
        const incoming = (incomingValue as ProgrammingExerciseBuildConfig | undefined) ?? undefined;
        const entries = [
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.sequentialTestRuns', key: 'sequentialTestRuns' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.buildPlanConfiguration', key: 'buildPlanConfiguration' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.buildScript', key: 'buildScript' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.checkoutSolutionRepository', key: 'checkoutSolutionRepository' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.testCheckoutPath', key: 'testCheckoutPath' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.assignmentCheckoutPath', key: 'assignmentCheckoutPath' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.solutionCheckoutPath', key: 'solutionCheckoutPath' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.timeoutSeconds', key: 'timeoutSeconds' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.dockerFlags', key: 'dockerFlags' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.theiaImage', key: 'theiaImage' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.allowBranching', key: 'allowBranching' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.branchRegex', key: 'branchRegex' },
        ] as const;

        return entries.map((entry) => ({
            labelKey: entry.labelKey,
            current: current ? (current as any)[entry.key] : undefined,
            incoming: incoming ? (incoming as any)[entry.key] : undefined,
        }));
    }

    /**
     * Normalizes category values into a trimmed list.
     */
    toCategoryList(value: unknown): string[] {
        if (!value) {
            return [];
        }
        if (Array.isArray(value)) {
            return value.map((entry) => String(entry)).filter((entry) => entry.trim().length > 0);
        }
        if (typeof value === 'string') {
            return value
                .split(',')
                .map((entry) => entry.trim())
                .filter((entry) => entry.length > 0);
        }
        return [String(value)];
    }

    /**
     * Maps competency links to display-friendly entries.
     */
    toCompetencyDisplay(value: unknown): Array<{ title: string; weight: number | undefined }> {
        const links = value as CompetencyExerciseLink[] | undefined;
        if (!links || !Array.isArray(links)) {
            return [];
        }
        return links
            .map((link) => ({
                title: link.competency?.title ?? '',
                weight: link.weight,
            }))
            .filter((entry) => entry.title);
    }

    /**
     * Converts raw values into diffable display text, replacing test ids when needed.
     */
    toDisplayText(field: string, value: unknown): string {
        if (value === undefined || value === null) {
            return '';
        }
        if (typeof value !== 'string') {
            try {
                return JSON.stringify(value, null, 2);
            } catch {
                return String(value);
            }
        }
        const text = value;
        if (!this.isProblemStatementField(field)) {
            return text;
        }
        this.testCasesVersion();
        return this.replaceTestIdsWithNames(text);
    }

    /**
     * Converts display text back to raw storage format for test ids.
     */
    toRawText(field: string, value: unknown): string {
        if (value === undefined || value === null) {
            return '';
        }
        const text = String(value);
        if (!this.isProblemStatementField(field)) {
            return text;
        }
        return this.replaceTestNamesWithIds(text);
    }

    /**
     * Produces a diff-friendly representation for non-string values.
     */
    toDiffText(field: string, value: unknown): string {
        if (value === undefined || value === null) {
            return '';
        }
        if (typeof value === 'string') {
            return this.isProblemStatementField(field) ? this.toDisplayText(field, value) : value;
        }
        try {
            return JSON.stringify(value, null, 2);
        } catch {
            return String(value);
        }
    }

    /**
     * Type guard for list values rendered in the quiz diff.
     */
    isListValue(value: unknown): value is string[] {
        return Array.isArray(value);
    }

    /**
     * Returns true when the value appears to be a translation key.
     */
    isTranslationKey(value: string | string[] | undefined): boolean {
        return typeof value === 'string' && value.startsWith('artemisApp.');
    }

    /**
     * Builds structured quiz question cards for side-by-side comparison.
     */
    getQuizQuestionCards(
        currentValue: unknown,
        incomingValue: unknown,
    ): Array<{
        key: string;
        title: string;
        typeKey: string;
        fields: Array<{ labelKey: string; current: string | string[] | undefined; incoming: string | string[] | undefined }>;
    }> {
        const current = Array.isArray(currentValue) ? (currentValue as QuizQuestion[]) : [];
        const incoming = Array.isArray(incomingValue) ? (incomingValue as QuizQuestion[]) : [];
        const pairs = this.pairQuizQuestions(current, incoming);
        return pairs.map((pair, index) => {
            const title = this.getQuizQuestionTitle(pair.current ?? pair.incoming, index);
            const typeKey = this.getQuizQuestionTypeKey(pair.current ?? pair.incoming);
            return {
                key: pair.key,
                title,
                typeKey,
                fields: this.buildQuizQuestionFields(pair.current, pair.incoming),
            };
        });
    }

    /**
     * Pairs quiz questions by id when available, otherwise by index.
     */
    private pairQuizQuestions(current: QuizQuestion[], incoming: QuizQuestion[]): Array<{ key: string; current?: QuizQuestion; incoming?: QuizQuestion }> {
        const currentById = new Map<number, QuizQuestion>();
        const incomingById = new Map<number, QuizQuestion>();
        for (const question of current) {
            if (question.id != undefined) {
                currentById.set(question.id, question);
            }
        }
        for (const question of incoming) {
            if (question.id != undefined) {
                incomingById.set(question.id, question);
            }
        }
        if (currentById.size > 0 || incomingById.size > 0) {
            const orderedKeys: number[] = [];
            for (const question of incoming) {
                if (question.id != undefined && !orderedKeys.includes(question.id)) {
                    orderedKeys.push(question.id);
                }
            }
            for (const question of current) {
                if (question.id != undefined && !orderedKeys.includes(question.id)) {
                    orderedKeys.push(question.id);
                }
            }
            return orderedKeys.map((id) => ({
                key: `id-${id}`,
                current: currentById.get(id),
                incoming: incomingById.get(id),
            }));
        }
        const maxLength = Math.max(current.length, incoming.length);
        const pairs: Array<{ key: string; current?: QuizQuestion; incoming?: QuizQuestion }> = [];
        for (let i = 0; i < maxLength; i++) {
            pairs.push({
                key: `idx-${i}`,
                current: current[i],
                incoming: incoming[i],
            });
        }
        return pairs;
    }

    /**
     * Resolves a quiz question title with a fallback label.
     */
    private getQuizQuestionTitle(question: QuizQuestion | undefined, index: number): string {
        if (question?.title) {
            return question.title;
        }
        return `Question ${index + 1}`;
    }

    /**
     * Maps quiz question types to translation keys.
     */
    private getQuizQuestionTypeKey(question: QuizQuestion | undefined): string {
        switch (question?.type) {
            case QuizQuestionType.MULTIPLE_CHOICE:
                return 'artemisApp.quizExercise.multipleChoiceQuestion';
            case QuizQuestionType.DRAG_AND_DROP:
                return 'artemisApp.quizExercise.dragAndDropQuestion';
            case QuizQuestionType.SHORT_ANSWER:
                return 'artemisApp.quizExercise.shortAnswerQuestion';
            default:
                return 'artemisApp.quizExercise.questions';
        }
    }

    /**
     * Builds the per-question field list for quiz conflicts.
     */
    private buildQuizQuestionFields(
        current?: QuizQuestion,
        incoming?: QuizQuestion,
    ): Array<{ labelKey: string; current: string | string[] | undefined; incoming: string | string[] | undefined }> {
        const fields: Array<{ labelKey: string; current: string | string[] | undefined; incoming: string | string[] | undefined }> = [
            { labelKey: 'artemisApp.quizQuestion.title', current: current?.title, incoming: incoming?.title },
            { labelKey: 'artemisApp.quizQuestion.text', current: current?.text, incoming: incoming?.text },
            { labelKey: 'artemisApp.quizQuestion.score', current: this.formatNumber(current?.points), incoming: this.formatNumber(incoming?.points) },
            { labelKey: 'artemisApp.quizQuestion.scoringType', current: this.formatScoringType(current?.scoringType), incoming: this.formatScoringType(incoming?.scoringType) },
            { labelKey: 'artemisApp.quizQuestion.randomizeOrder', current: this.formatBoolean(current?.randomizeOrder), incoming: this.formatBoolean(incoming?.randomizeOrder) },
            { labelKey: 'artemisApp.quizQuestion.hint', current: current?.hint, incoming: incoming?.hint },
            { labelKey: 'artemisApp.quizQuestion.explanation', current: current?.explanation, incoming: incoming?.explanation },
        ];

        if (current?.type === QuizQuestionType.MULTIPLE_CHOICE || incoming?.type === QuizQuestionType.MULTIPLE_CHOICE) {
            fields.push({
                labelKey: 'artemisApp.quizExercise.options',
                current: this.formatAnswerOptions((current as MultipleChoiceQuestion | undefined)?.answerOptions),
                incoming: this.formatAnswerOptions((incoming as MultipleChoiceQuestion | undefined)?.answerOptions),
            });
        }
        if (current?.type === QuizQuestionType.DRAG_AND_DROP || incoming?.type === QuizQuestionType.DRAG_AND_DROP) {
            const currentDnd = current as DragAndDropQuestion | undefined;
            const incomingDnd = incoming as DragAndDropQuestion | undefined;
            fields.push(
                {
                    labelKey: 'artemisApp.dragAndDropQuestion.dragItems',
                    current: this.formatItemList(currentDnd?.dragItems, (item) => item.text),
                    incoming: this.formatItemList(incomingDnd?.dragItems, (item) => item.text),
                },
                {
                    labelKey: 'artemisApp.dragAndDropQuestion.dropLocations',
                    current: this.formatCount(currentDnd?.dropLocations?.length),
                    incoming: this.formatCount(incomingDnd?.dropLocations?.length),
                },
                {
                    labelKey: 'artemisApp.dragAndDropQuestion.correctMappings',
                    current: this.formatCount(currentDnd?.correctMappings?.length),
                    incoming: this.formatCount(incomingDnd?.correctMappings?.length),
                },
            );
        }
        if (current?.type === QuizQuestionType.SHORT_ANSWER || incoming?.type === QuizQuestionType.SHORT_ANSWER) {
            const currentShort = current as ShortAnswerQuestion | undefined;
            const incomingShort = incoming as ShortAnswerQuestion | undefined;
            fields.push(
                {
                    labelKey: 'artemisApp.shortAnswerQuestion.solutions',
                    current: this.formatItemList(currentShort?.solutions, (solution) => solution.text),
                    incoming: this.formatItemList(incomingShort?.solutions, (solution) => solution.text),
                },
                {
                    labelKey: 'artemisApp.shortAnswerQuestion.spots',
                    current: this.formatCount(currentShort?.spots?.length),
                    incoming: this.formatCount(incomingShort?.spots?.length),
                },
                {
                    labelKey: 'artemisApp.quizExercise.matchLetterCase.title',
                    current: this.formatBoolean(currentShort?.matchLetterCase),
                    incoming: this.formatBoolean(incomingShort?.matchLetterCase),
                },
                {
                    labelKey: 'artemisApp.quizExercise.matchPercentage.title',
                    current: this.formatNumber(currentShort?.similarityValue),
                    incoming: this.formatNumber(incomingShort?.similarityValue),
                },
            );
        }

        return fields.filter((field) => field.current !== undefined || field.incoming !== undefined);
    }

    /**
     * Formats answer options for display, marking correct ones.
     */
    private formatAnswerOptions(options: AnswerOption[] | undefined): string[] | undefined {
        return this.formatItemList(options, (option) => {
            const text = option.text ?? '';
            return option.isCorrect ? `${text} (correct)` : text;
        });
    }

    /**
     * Formats a list with a maximum number of items, adding a "+N more" suffix.
     */
    private formatItemList<T>(items: T[] | undefined, toText: (item: T) => string | undefined, maxItems = 5): string[] | undefined {
        if (!items || items.length === 0) {
            return undefined;
        }
        const texts = items.map((item) => toText(item)).filter((text): text is string => Boolean(text && text.trim().length > 0));
        if (texts.length <= maxItems) {
            return texts;
        }
        const head = texts.slice(0, maxItems);
        const rest = texts.length - maxItems;
        head.push(`+${rest} more`);
        return head;
    }

    /**
     * Formats a boolean into a translation key when defined.
     */
    private formatBoolean(value: boolean | undefined): string | undefined {
        if (value === undefined) {
            return undefined;
        }
        return value ? 'artemisApp.global.yes' : 'artemisApp.global.no';
    }

    /**
     * Formats a number value when defined.
     */
    private formatNumber(value: number | undefined): string | undefined {
        if (value === undefined || value === null) {
            return undefined;
        }
        return String(value);
    }

    /**
     * Formats a count value when defined.
     */
    private formatCount(value: number | undefined): string | undefined {
        if (value === undefined || value === null) {
            return undefined;
        }
        return String(value);
    }

    /**
     * Formats the scoring type into a translation key.
     */
    private formatScoringType(value: ScoringType | undefined): string | undefined {
        if (!value) {
            return undefined;
        }
        const key = value.toLowerCase();
        return `artemisApp.quizExercise.scoringType.${key}`;
    }

    /**
     * Retrieves the resolved text for a field, falling back to the incoming text.
     */
    getResolvedText(field: string, fallback: string): string {
        return this.resolvedTexts()[field] ?? fallback;
    }

    /**
     * Updates the resolved text and optionally marks the field as touched.
     */
    updateResolvedText(field: string, value: string, markTouched = true): void {
        const updated = Object.assign({}, this.resolvedTexts());
        updated[field] = value;
        this.resolvedTexts.set(updated);
        if (markTouched) {
            const touched = Object.assign({}, this.resolvedTouched());
            touched[field] = true;
            this.resolvedTouched.set(touched);
        }
    }

    /**
     * Handles modified content emitted by the diff editor.
     */
    onDiffModified(field: string, value: string): void {
        const initialized = this.resolvedInitialized()[field];
        if (!initialized) {
            const updated = Object.assign({}, this.resolvedInitialized());
            updated[field] = true;
            this.resolvedInitialized.set(updated);
            this.updateResolvedText(field, value, false);
            return;
        }
        this.updateResolvedText(field, value, true);
    }

    /**
     * Sets the resolved text to the current value.
     */
    useCurrentResolvedText(field: string, value: unknown): void {
        this.updateResolvedText(field, this.toDisplayText(field, value));
    }

    /**
     * Sets the resolved text to the incoming value.
     */
    useIncomingResolvedText(field: string, value: unknown): void {
        this.updateResolvedText(field, this.toDisplayText(field, value));
    }

    /**
     * Returns a resolved value for editable diff fields when the user modified them.
     */
    private getResolvedValue(conflict: ExerciseMetadataConflictItem, fallbackToIncoming = true): unknown {
        if (!this.isEditableDiffField(conflict.field)) {
            return undefined;
        }
        const resolved = this.resolvedTexts()[conflict.field];
        if (!this.resolvedTouched()[conflict.field]) {
            return undefined;
        }
        const fallback = fallbackToIncoming ? this.toDisplayText(conflict.field, conflict.incomingValue) : this.toDisplayText(conflict.field, conflict.currentValue);
        return this.toRawText(conflict.field, resolved ?? fallback);
    }

    /**
     * Loads test cases only when needed for problem statement replacements.
     */
    private maybeLoadTestCases(): void {
        if (this.testCasesLoading || this.testCasesLoaded) {
            return;
        }
        if (this.exerciseType() !== ExerciseType.PROGRAMMING) {
            return;
        }
        const exerciseId = this.exerciseId();
        if (!exerciseId) {
            return;
        }
        if (!this.conflicts().some((conflict) => conflict.field === 'problemStatement')) {
            return;
        }
        this.loadTestCases(exerciseId);
    }

    /**
     * Fetches programming test cases and builds id/name lookup tables.
     */
    private async loadTestCases(exerciseId: number): Promise<void> {
        if (this.testCasesLoading || this.testCasesLoaded) {
            return;
        }
        this.testCasesLoading = true;
        try {
            const testCases = await firstValueFrom(this.programmingExerciseGradingService.getTestCases(exerciseId).pipe(catchError(() => of([] as ProgrammingExerciseTestCase[]))));
            this.testCaseNameById = new Map();
            this.testCaseIdByName = new Map();
            for (const testCase of testCases) {
                if (testCase.id != undefined && testCase.testName) {
                    this.testCaseNameById.set(testCase.id, testCase.testName);
                    this.testCaseIdByName.set(testCase.testName, testCase.id);
                }
            }
            this.testCasesLoaded = true;
            this.testCasesVersion.update((value) => value + 1);
        } finally {
            this.testCasesLoading = false;
        }
    }

    /**
     * Replaces <testid> entries containing ids with their corresponding names.
     */
    private replaceTestIdsWithNames(value: string): string {
        if (this.testCaseNameById.size === 0) {
            return value;
        }
        return value.replace(/<testid>(\d+)<\/testid>/g, (match, id) => {
            const name = this.testCaseNameById.get(Number(id));
            return name ? `<testid>${name}</testid>` : match;
        });
    }

    /**
     * Replaces <testid> entries containing names with their corresponding ids.
     */
    private replaceTestNamesWithIds(value: string): string {
        if (this.testCaseIdByName.size === 0) {
            return value;
        }
        return value.replace(/<testid>([^<]+)<\/testid>/g, (match, text) => {
            if (/^\d+$/.test(text)) {
                return match;
            }
            const id = this.testCaseIdByName.get(text);
            return id != undefined ? `<testid>${id}</testid>` : match;
        });
    }
}

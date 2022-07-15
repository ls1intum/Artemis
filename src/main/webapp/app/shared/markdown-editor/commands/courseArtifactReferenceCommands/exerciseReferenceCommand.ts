import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { MetisService } from 'app/shared/metis/metis.service';

export class ExerciseReferenceCommand extends MultiOptionCommand {
    metisService: MetisService;

    buttonTranslationString = 'artemisApp.metis.editor.exercise';

    constructor(metisService: MetisService) {
        super();
        this.metisService = metisService;

        this.setValues(
            this.metisService.getCourse().exercises!?.map((exercise) => ({
                id: exercise.id!.toString(),
                value: exercise.title!,
                type: exercise.type,
            })),
        );
    }

    /**
     * @function execute
     * @param {string} selectedExerciseId   ID of the exercise to be referenced
     * @desc                                Add an exercise reference link in markdown language
     *                                      1. Add '[{exercise-title}](/courses/{courseId}/exercises/{exerciseId}})' at the cursor in the editor
     *                                      2. Link in markdown language appears which when clicked navigates to the exercise page
     */
    execute(selectedExerciseId: string): void {
        const selectedExercise = this.getValues().find((value) => value.id.toString() === selectedExerciseId)!;
        const referenceLink = `[${selectedExercise.type}]${selectedExercise.value}(${this.metisService.getLinkForExercise(selectedExercise.id)})[/${selectedExercise.type}]`;
        this.insertText(referenceLink);
        this.focus();
    }
}

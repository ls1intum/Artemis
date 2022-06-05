import { MetisService } from 'app/shared/metis/metis.service';
import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';

export class ExerciseReferenceCommand extends MultiOptionCommand {
    metisService: MetisService;

    buttonTranslationString = 'artemisApp.metis.editor.exercise';

    constructor(metisService: MetisService) {
        super();
        this.metisService = metisService;
    }

    /**
     * @function execute
     * @param {string} selectedExerciseId   ID of the exercise to be referenced
     * @desc                                Add an exercise reference link in markdown language
     *                                      1. Add ('[{exercise-title}](https://{client-address}/course/{courseId}/exercise/{exerciseId}})') at the cursor in the editor
     *                                      2. Link in markdown language appears which when clicked navigates to the exercise page
     */
    execute(selectedExerciseId: string): void {
        const selectedExercise = this.getValues().find((value) => value.id.toString() === selectedExerciseId);
        const referenceLink = '[' + selectedExercise!.value + '](' + this.metisService.getLinkForExercise(selectedExercise!.id) + ')';

        const range = this.getRange();
        this.replace(range, referenceLink);
        this.focus();
    }
}

import { DomainMultiOptionCommand } from 'app/shared/markdown-editor/domainCommands/domainMultiOptionCommand';
import { MetisService } from 'app/shared/metis/metis.service';

export class ExerciseReferenceCommand extends DomainMultiOptionCommand {
    metisService: MetisService;

    constructor(metisService: MetisService) {
        super();
        this.metisService = metisService;
    }

    buttonTranslationString = 'artemisApp.metis.editor.exercise';

    getOpeningIdentifier(): string {
        return '[';
    }

    getClosingIdentifier(): string {
        return ')';
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

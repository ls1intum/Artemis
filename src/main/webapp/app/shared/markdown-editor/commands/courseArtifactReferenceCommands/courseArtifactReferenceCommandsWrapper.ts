import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/exerciseReferenceCommand';
import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { LectureReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/lectureReferenceCommand';

export class CourseArtifactReferenceCommandsWrapper {
    metisService: MetisService;

    buttonTranslationString = 'artemisApp.metis.editor.reference';

    constructor(metisService: MetisService) {
        this.metisService = metisService;
    }

    static getCommands(metisService: MetisService): MultiOptionCommand[] {
        return [new ExerciseReferenceCommand(metisService), new LectureReferenceCommand(metisService)];
    }
}

import { Pipe, PipeTransform } from '@angular/core';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';

/**
 * Pipe to transform an ExerciseHint into a CodeHint
 */
@Pipe({
    name: 'castToCodeHint',
    pure: true,
})
export class CastToCodeHintPipe implements PipeTransform {
    transform(exerciseHint: ExerciseHint): CodeHint {
        return exerciseHint as CodeHint;
    }
}

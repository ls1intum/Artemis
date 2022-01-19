import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { ITextHintService, TextHintResponse } from 'app/exercises/shared/exercise-hint/manage/text-hint.service';
import { TextHint } from 'app/entities/hestia/text-hint-model';

export class MockTextHintService implements ITextHintService {
    private textHintDummy = { id: 1 } as TextHint;
    private textHintDummy2 = { id: 2 } as TextHint;

    create(textHint: TextHint): Observable<TextHintResponse> {
        return of({ body: this.textHintDummy }) as Observable<TextHintResponse>;
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return of();
    }

    find(id: number): Observable<TextHintResponse> {
        return of({ body: this.textHintDummy }) as Observable<TextHintResponse>;
    }

    findByExerciseId(exerciseId: number): Observable<HttpResponse<TextHint[]>> {
        return of({ body: [this.textHintDummy, this.textHintDummy2] }) as Observable<HttpResponse<TextHint[]>>;
    }

    update(textHint: TextHint): Observable<TextHintResponse> {
        return of({ body: this.textHintDummy }) as Observable<TextHintResponse>;
    }
}

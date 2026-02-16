import { Injectable } from '@angular/core';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

@Injectable({
    providedIn: 'root',
})
export class LLMSelectionModalService {
    private openModalSubject = new Subject<LLMSelectionDecision | undefined>();
    private choiceSubject = new Subject<LLMSelectionDecision>();

    openModal$: Observable<LLMSelectionDecision | undefined> = this.openModalSubject.asObservable();
    choice$: Observable<LLMSelectionDecision> = this.choiceSubject.asObservable();

    open(currentSelection?: LLMSelectionDecision): Promise<LLMSelectionDecision> {
        this.openModalSubject.next(currentSelection);
        return firstValueFrom(this.choice$);
    }

    emitChoice(choice: LLMSelectionDecision): void {
        this.choiceSubject.next(choice);
    }
}

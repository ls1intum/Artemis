import { Injectable } from '@angular/core';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { LLMModalResult, LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

@Injectable({
    providedIn: 'root',
})
export class LLMSelectionModalService {
    private openModalSubject = new Subject<LLMSelectionDecision | undefined>();
    private choiceSubject = new Subject<LLMModalResult>();

    openModal$: Observable<LLMSelectionDecision | undefined> = this.openModalSubject.asObservable();
    choice$: Observable<LLMModalResult> = this.choiceSubject.asObservable();

    open(currentSelection?: LLMSelectionDecision): Promise<LLMModalResult> {
        this.openModalSubject.next(currentSelection);
        return firstValueFrom(this.choice$);
    }

    emitChoice(choice: LLMModalResult): void {
        this.choiceSubject.next(choice);
    }
}

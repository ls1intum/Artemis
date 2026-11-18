import { Service, signal } from '@angular/core';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { LLMModalResult, LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

@Service()
export class LLMSelectionModalService {
    readonly isOpen = signal(false);
    private openModalSubject = new Subject<LLMSelectionDecision | undefined>();
    private choiceSubject = new Subject<LLMModalResult>();

    openModal$: Observable<LLMSelectionDecision | undefined> = this.openModalSubject.asObservable();
    choice$: Observable<LLMModalResult> = this.choiceSubject.asObservable();

    open(currentSelection?: LLMSelectionDecision): Promise<LLMModalResult> {
        this.isOpen.set(true);
        this.openModalSubject.next(currentSelection);
        return firstValueFrom(this.choice$);
    }

    emitChoice(choice: LLMModalResult): void {
        this.isOpen.set(false);
        this.choiceSubject.next(choice);
    }
}

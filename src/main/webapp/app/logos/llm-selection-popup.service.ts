import { Injectable } from '@angular/core';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { LLMSelectionChoice } from './llm-selection-popup.component';

@Injectable({
    providedIn: 'root',
})
export class LLMSelectionModalService {
    private openModalSubject = new Subject<LLMSelectionChoice | undefined>();
    private choiceSubject = new Subject<LLMSelectionChoice>();

    openModal$: Observable<LLMSelectionChoice | undefined> = this.openModalSubject.asObservable();
    choice$: Observable<LLMSelectionChoice> = this.choiceSubject.asObservable();

    open(currentSelection?: LLMSelectionChoice): Promise<LLMSelectionChoice> {
        this.openModalSubject.next(currentSelection);
        return firstValueFrom(this.choice$);
    }

    emitChoice(choice: LLMSelectionChoice): void {
        this.choiceSubject.next(choice);
    }
}

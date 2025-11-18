import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { LLMSelectionChoice } from './llm-selection-popup.component';

@Injectable({
    providedIn: 'root',
})
export class LLMSelectionModalService {
    private openModalSubject = new Subject<void>();
    private choiceSubject = new Subject<LLMSelectionChoice>();

    openModal$: Observable<void> = this.openModalSubject.asObservable();
    choice$: Observable<LLMSelectionChoice> = this.choiceSubject.asObservable();

    open(): Promise<LLMSelectionChoice> {
        this.openModalSubject.next();
        return new Promise((resolve) => {
            const subscription = this.choice$.subscribe((choice) => {
                resolve(choice);
                subscription.unsubscribe();
            });
        });
    }

    emitChoice(choice: LLMSelectionChoice): void {
        this.choiceSubject.next(choice);
    }
}

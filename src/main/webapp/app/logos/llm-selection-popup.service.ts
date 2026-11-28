import { Service, computed, signal } from '@angular/core';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { LLMModalResult, LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

/** A request to show the popup, with the choice that the user has made so far. */
export interface LLMSelectionModalRequest {
    currentSelection?: LLMSelectionDecision;
}

/**
 * Opens the popup in which the user chooses how AI features may be used, and returns the choice.
 *
 * The popup is created when the first request arrives (see `AppComponent`), so a request is kept as state in {@link pendingRequest} until the user answers:
 * a popup that does not exist yet reads it when it is created, which an event on {@link openModal$} could not tell it, because an event has no
 * subscriber before the popup exists. A popup that exists is told through {@link openModal$}.
 */
@Service()
export class LLMSelectionModalService {
    /** The request that the user has not answered yet. */
    readonly pendingRequest = signal<LLMSelectionModalRequest | undefined>(undefined);
    /** Whether the popup is open, that is, whether a request waits for an answer. */
    readonly isOpen = computed(() => this.pendingRequest() !== undefined);
    private openModalSubject = new Subject<LLMSelectionDecision | undefined>();
    private choiceSubject = new Subject<LLMModalResult>();

    openModal$: Observable<LLMSelectionDecision | undefined> = this.openModalSubject.asObservable();
    choice$: Observable<LLMModalResult> = this.choiceSubject.asObservable();

    open(currentSelection?: LLMSelectionDecision): Promise<LLMModalResult> {
        this.pendingRequest.set({ currentSelection });
        this.openModalSubject.next(currentSelection);
        return firstValueFrom(this.choice$);
    }

    emitChoice(choice: LLMModalResult): void {
        this.pendingRequest.set(undefined);
        this.choiceSubject.next(choice);
    }
}

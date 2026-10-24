import { Directive } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';
import { vi } from 'vitest';
import { CommunicationService } from 'app/communication/service/communication.service';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PostingCreateEditDirective } from './posting-create-edit.directive';

@Directive({ selector: '[postingSubmitProbe]' })
class PostingSubmitProbe extends PostingCreateEditDirective<AnswerPost> {
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({ content: 'draft' });
    }
    createPosting(): void {}
    updatePosting(): void {}
    send(request: (payload: AnswerPost) => Observable<AnswerPost>, onSuccess: (posting: AnswerPost) => void): void {
        this.submitPosting(request, onSuccess);
    }
}

describe('PostingCreateEditDirective submission', () => {
    const create = () => {
        TestBed.configureTestingModule({ providers: [{ provide: CommunicationService, useValue: {} }] });
        return TestBed.createDirective(PostingSubmitProbe, { tagName: 'div' });
    };

    it('does not submit a removed posting and releases its loading state', () => {
        const fixture = create();
        const component = fixture.directiveInstance;
        component.isLoading.set(true);
        const request = vi.fn(() => of(new AnswerPost()));
        const success = vi.fn();
        component.send(request, success);
        expect(component.isLoading()).toBe(false);
        expect(request).not.toHaveBeenCalled();
        expect(success).not.toHaveBeenCalled();
    });

    it('submits an independent form payload and leaves success cleanup ordering to the editor', () => {
        const fixture = create();
        const component = fixture.directiveInstance;
        const source = new AnswerPost();
        source.content = 'original';
        source.post = { id: 7, content: 'parent' };
        component.posting.set(source);
        fixture.detectChanges();
        component.formGroup.setValue({ content: 'draft answer' });
        component.isLoading.set(true);
        const response = new Subject<AnswerPost>();
        const request = vi.fn((_payload: AnswerPost) => response);
        const success = vi.fn(() => {
            expect(component.isLoading()).toBe(true);
            component.isLoading.set(false);
        });
        component.send(request, success);
        const payload = request.mock.calls[0][0];
        expect(payload).not.toBe(source);
        expect(payload).toBeInstanceOf(AnswerPost);
        expect(payload.post).not.toBe(source.post);
        expect(payload.content).toBe('draft answer');
        expect(source.content).toBe('original');
        expect(component.isLoading()).toBe(true);
        const saved = new AnswerPost();
        saved.id = 10;
        response.next(saved);
        expect(success).toHaveBeenCalledExactlyOnceWith(saved);
        expect(component.isLoading()).toBe(false);
        expect(component.posting()).toBe(source);
    });

    it('preserves the input and form draft on failure while stopping the loading indicator', () => {
        const fixture = create();
        const component = fixture.directiveInstance;
        component.posting.set({ id: 1, content: 'original' });
        fixture.detectChanges();
        component.formGroup.setValue({ content: 'retry me' });
        component.isLoading.set(true);
        const response = new Subject<AnswerPost>();
        const success = vi.fn();
        component.send(() => response, success);
        response.error(new Error('request failed'));
        expect(component.isLoading()).toBe(false);
        expect(component.posting()?.content).toBe('original');
        expect(component.formGroup.get('content')?.value).toBe('retry me');
        expect(success).not.toHaveBeenCalled();
    });
});

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { InlineRefinementButtonComponent } from './inline-refinement-button.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Subject } from 'rxjs';

describe('InlineRefinementButtonComponent', () => {
    let fixture: ComponentFixture<InlineRefinementButtonComponent>;
    let comp: InlineRefinementButtonComponent;
    let translateService: TranslateService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [InlineRefinementButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(InlineRefinementButtonComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);

        // Set required inputs
        fixture.componentRef.setInput('top', 100);
        fixture.componentRef.setInput('left', 200);
        fixture.componentRef.setInput('selectedText', 'Some selected text');
        fixture.componentRef.setInput('startLine', 1);
        fixture.componentRef.setInput('endLine', 2);
        fixture.componentRef.setInput('startColumn', 5);
        fixture.componentRef.setInput('endColumn', 10);

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });

    it('should initialize with collapsed state', () => {
        expect(comp.isExpanded()).toBeFalse();
        expect(comp.instruction).toBe('');
    });

    it('should expand and focus input when expand is called', fakeAsync(() => {
        comp.expand();
        tick(100);

        expect(comp.isExpanded()).toBeTrue();
    }));

    it('should emit refine event with correct data on submit', () => {
        const refineSpy = jest.spyOn(comp.refine, 'emit');

        comp.expand();
        comp.instruction = 'Improve clarity';
        comp.submit();

        expect(refineSpy).toHaveBeenCalledWith({
            instruction: 'Improve clarity',
            startLine: 1,
            endLine: 2,
            startColumn: 5,
            endColumn: 10,
        });
    });

    it('should not emit refine event when instruction is empty', () => {
        const refineSpy = jest.spyOn(comp.refine, 'emit');

        comp.expand();
        comp.instruction = '';
        comp.submit();

        expect(refineSpy).not.toHaveBeenCalled();
    });

    it('should not emit refine event when instruction is only whitespace', () => {
        const refineSpy = jest.spyOn(comp.refine, 'emit');

        comp.expand();
        comp.instruction = '   ';
        comp.submit();

        expect(refineSpy).not.toHaveBeenCalled();
    });

    it('should not emit refine event when isLoading is true', () => {
        const refineSpy = jest.spyOn(comp.refine, 'emit');

        fixture.componentRef.setInput('isLoading', true);
        fixture.detectChanges();

        comp.expand();
        comp.instruction = 'Test instruction';
        comp.submit();

        expect(refineSpy).not.toHaveBeenCalled();
    });

    it('should submit on Enter key press', () => {
        const submitSpy = jest.spyOn(comp, 'submit');
        const event = new KeyboardEvent('keydown', { key: 'Enter' });
        jest.spyOn(event, 'stopPropagation');
        jest.spyOn(event, 'preventDefault');

        comp.onKeydown(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(event.preventDefault).toHaveBeenCalled();
        expect(submitSpy).toHaveBeenCalled();
    });

    it('should not submit on Shift+Enter key press', () => {
        const submitSpy = jest.spyOn(comp, 'submit');
        const event = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true });

        comp.onKeydown(event);

        expect(submitSpy).not.toHaveBeenCalled();
    });

    it('should emit closeRefinement on Escape key press', () => {
        const closeSpy = jest.spyOn(comp.closeRefinement, 'emit');
        const event = new KeyboardEvent('keydown', { key: 'Escape' });

        comp.onKeydown(event);

        expect(closeSpy).toHaveBeenCalled();
    });

    it('should emit closeRefinement when handleClose is called', () => {
        const closeSpy = jest.spyOn(comp.closeRefinement, 'emit');

        comp.handleClose();

        expect(closeSpy).toHaveBeenCalled();
    });

    it('should trigger change detection on language change', fakeAsync(() => {
        const langChangeSubject = new Subject<any>();
        (translateService as any).onLangChange = langChangeSubject.asObservable();

        // Re-create component to pick up the new observable
        fixture = TestBed.createComponent(InlineRefinementButtonComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('top', 100);
        fixture.componentRef.setInput('left', 200);
        fixture.componentRef.setInput('selectedText', 'text');
        fixture.componentRef.setInput('startLine', 1);
        fixture.componentRef.setInput('endLine', 1);
        fixture.componentRef.setInput('startColumn', 0);
        fixture.componentRef.setInput('endColumn', 5);

        // Spy on the new component's cdr BEFORE detectChanges
        const cdrSpy = jest.spyOn(comp['cdr'], 'markForCheck');
        fixture.detectChanges();

        langChangeSubject.next({ lang: 'de' });

        expect(cdrSpy).toHaveBeenCalled();
    }));

    it('should have correct input values', () => {
        expect(comp.top()).toBe(100);
        expect(comp.left()).toBe(200);
        expect(comp.selectedText()).toBe('Some selected text');
        expect(comp.startLine()).toBe(1);
        expect(comp.endLine()).toBe(2);
        expect(comp.startColumn()).toBe(5);
        expect(comp.endColumn()).toBe(10);
    });

    it('should stop propagation on all keydown events', () => {
        const event = new KeyboardEvent('keydown', { key: 'a' });
        jest.spyOn(event, 'stopPropagation');

        comp.onKeydown(event);

        expect(event.stopPropagation).toHaveBeenCalled();
    });
});

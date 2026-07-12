import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MonacoEditorFitTextComponent } from './monaco-editor-fit-text.component';
import { MockComponent, MockInstance } from 'ng-mocks';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';

describe('MonacoEditorFitTextComponent', () => {
    setupTestBed({ zoneless: true });
    MockInstance.scope();

    let component: MonacoEditorFitTextComponent;
    let fixture: ComponentFixture<MonacoEditorFitTextComponent>;
    let contentHeight: number;

    beforeEach(async () => {
        contentHeight = 48;
        MockInstance(MonacoEditorComponent, 'getContentHeight', () => contentHeight);

        await TestBed.configureTestingModule({
            imports: [MonacoEditorFitTextComponent],
        })
            .overrideComponent(MonacoEditorFitTextComponent, {
                remove: { imports: [MonacoEditorComponent] },
                add: { imports: [MockComponent(MonacoEditorComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(MonacoEditorFitTextComponent);
        component = fixture.componentInstance;
    });

    describe('initialization', () => {
        it('should create', () => {
            fixture.detectChanges();

            const monacoEditor = fixture.debugElement.query(By.directive(MonacoEditorComponent));
            expect(monacoEditor).not.toBeNull();
        });

        it('should have empty text by default', () => {
            fixture.detectChanges();
            expect(component.text()).toBe('');
        });

        it('should accept initial text value via setInput', () => {
            fixture.componentRef.setInput('text', 'initial script');
            fixture.detectChanges();

            expect(component.text()).toBe('initial script');
        });

        it('should initialize the wrapper height from the editor content height', () => {
            fixture.detectChanges();

            const wrapper = fixture.debugElement.query(By.css('.border')).nativeElement as HTMLElement;
            expect(wrapper.style.height).toBe(`${contentHeight}px`);
        });
    });

    describe('text model', () => {
        it('should update text via setInput', () => {
            fixture.detectChanges();
            fixture.componentRef.setInput('text', 'updated text');
            fixture.detectChanges();

            expect(component.text()).toBe('updated text');
        });

        it('should allow setting text via signal', () => {
            fixture.detectChanges();
            component.text.set('new value from signal');

            expect(component.text()).toBe('new value from signal');
        });
    });

    describe('editor height', () => {
        it('should update the wrapper height when the editor content height changes', () => {
            fixture.detectChanges();

            fixture.debugElement.query(By.directive(MonacoEditorComponent)).triggerEventHandler('contentHeightChanged', 64);
            fixture.detectChanges();

            const wrapper = fixture.debugElement.query(By.css('.border')).nativeElement as HTMLElement;
            expect(wrapper.style.height).toBe('64px');
        });
    });
});

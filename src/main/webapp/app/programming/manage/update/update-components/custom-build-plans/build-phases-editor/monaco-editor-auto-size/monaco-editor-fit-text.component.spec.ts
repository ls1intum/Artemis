import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MonacoEditorFitTextComponent } from './monaco-editor-fit-text.component';
import { MockComponent } from 'ng-mocks';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

describe('MonacoEditorFitTextComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MonacoEditorFitTextComponent;
    let fixture: ComponentFixture<MonacoEditorFitTextComponent>;

    beforeEach(async () => {
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
});

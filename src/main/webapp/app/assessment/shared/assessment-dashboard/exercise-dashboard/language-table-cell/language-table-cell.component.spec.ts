import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LanguageTableCellComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/language-table-cell/language-table-cell.component';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Language } from 'app/core/course/shared/entities/course.model';
import { MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('LanguageTableCellComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LanguageTableCellComponent;
    let fixture: ComponentFixture<LanguageTableCellComponent>;

    const createTextSubmission = (language?: Language): TextSubmission => {
        const submission = new TextSubmission();
        submission.id = 1;
        submission.text = 'Test submission text';
        submission.language = language;
        return submission;
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(LanguageTableCellComponent, {
                remove: { imports: [ArtemisTranslatePipe] },
                add: { imports: [MockPipe(ArtemisTranslatePipe, (value) => value ?? '')] },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LanguageTableCellComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('component creation', () => {
        it('should create the component', () => {
            const submission = createTextSubmission(Language.ENGLISH);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            expect(component).toBeTruthy();
        });
    });

    describe('inputs', () => {
        it('should accept submission input', () => {
            const submission = createTextSubmission(Language.ENGLISH);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            expect(component.submission()).toBe(submission);
        });
    });

    describe('textSubmission computed', () => {
        it('should return the submission as TextSubmission', () => {
            const submission = createTextSubmission(Language.GERMAN);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            const textSubmission = component.textSubmission();
            expect(textSubmission).toBe(submission);
        });

        it('should preserve language property', () => {
            const submission = createTextSubmission(Language.ENGLISH);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            const textSubmission = component.textSubmission();
            expect(textSubmission.language).toBe(Language.ENGLISH);
        });
    });

    describe('template rendering', () => {
        it('should render a span element', () => {
            const submission = createTextSubmission(Language.ENGLISH);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            const span = fixture.nativeElement.querySelector('span');
            expect(span).toBeTruthy();
        });

        it('should display language translation key for known language', () => {
            const submission = createTextSubmission(Language.ENGLISH);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            const span = fixture.nativeElement.querySelector('span');
            expect(span.textContent).toContain('ENGLISH');
        });

        it('should display UNKNOWN when language is not set', () => {
            const submission = createTextSubmission(undefined);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            const span = fixture.nativeElement.querySelector('span');
            expect(span.textContent).toContain('UNKNOWN');
        });
    });

    describe('different languages', () => {
        it('should handle German language', () => {
            const submission = createTextSubmission(Language.GERMAN);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            expect(component.textSubmission().language).toBe(Language.GERMAN);
        });

        it('should handle English language', () => {
            const submission = createTextSubmission(Language.ENGLISH);
            fixture.componentRef.setInput('submission', submission);
            fixture.detectChanges();

            expect(component.textSubmission().language).toBe(Language.ENGLISH);
        });
    });
});

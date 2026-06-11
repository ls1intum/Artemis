import { vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { ExerciseUpdateNotificationComponent } from 'app/exercise/exercise-update-notification/exercise-update-notification.component';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';

describe('FormFooterComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<FormFooterComponent>;
    let comp: FormFooterComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(ExerciseUpdateNotificationComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FormFooterComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('update title depending on input signals', () => {
        fixture.componentRef.setInput('isCreation', true);
        fixture.componentRef.setInput('isImport', false);
        expect(comp.saveTitle()).toBe('entity.action.generate');

        fixture.componentRef.setInput('isImport', true);
        expect(comp.saveTitle()).toBe('entity.action.import');

        fixture.componentRef.setInput('isImport', false);
        fixture.componentRef.setInput('isCreation', false);

        expect(comp.saveTitle()).toBe('entity.action.save');
    });

    it('should display saving badge when isSaving is true', () => {
        fixture.componentRef.setInput('isSaving', true);
        fixture.detectChanges();
        const savingBadge = fixture.debugElement.query(By.css('.badge.bg-secondary'));
        expect(savingBadge).toBeTruthy();
    });

    it('should not display the exercise update notification when in creation or import mode', () => {
        fixture.componentRef.setInput('isCreation', true);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();
        const notificationComponent = fixture.debugElement.query(By.css('jhi-exercise-update-notification'));
        expect(notificationComponent).toBeNull();
    });

    it('should display invalid input badge when there are invalid reasons', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: 'test.value' }]);
        fixture.detectChanges();
        const invalidBadge = fixture.debugElement.query(By.css('.badge.bg-danger'));
        expect(invalidBadge).toBeTruthy();
    });

    it('should enable save button when form is valid', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isSaving', false);
        fixture.componentRef.setInput('isGeneratingWithAi', false);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.disabled).toBeFalsy();
    });

    it('should disable save button when saving is in progress', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isSaving', true);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.disabled).toBeTruthy();
    });

    it('should disable save button while generating with AI', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isGeneratingWithAi', true);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.disabled).toBeTruthy();
    });

    it('should no longer expose the relocated generate-with-AI action', () => {
        // The "Generate with AI" action was relocated into the problem component; the footer must not render it or expose its API.
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#generate-with-ai'))).toBeNull();
        expect((comp as unknown as Record<string, unknown>).showGenerateWithAi).toBeUndefined();
        expect((comp as unknown as Record<string, unknown>).generateWithAi).toBeUndefined();
    });

    describe('AI mode', () => {
        it('labels the primary action "Generate entire exercise" in AI mode', () => {
            fixture.componentRef.setInput('isCreation', true);
            fixture.componentRef.setInput('isAiMode', true);
            expect(comp.saveTitle()).toBe('artemisApp.programmingExercise.generateExercise.generateEntire');
        });

        it('renders the 3-way mode selector instead of the simple/advanced switch when AI mode is available', () => {
            fixture.componentRef.setInput('aiModeAvailable', true);
            fixture.componentRef.setInput('editMode', 'ai');
            fixture.detectChanges();
            expect(fixture.debugElement.query(By.css('p-selectButton'))).not.toBeNull();
            expect(fixture.debugElement.query(By.css('jhi-switch-edit-mode-button'))).toBeNull();
        });

        it('emits setEditMode from the mode selector', () => {
            const emitted: string[] = [];
            comp.setEditMode.subscribe((mode) => emitted.push(mode));
            comp.onModeChange('advanced');
            expect(emitted).toEqual(['advanced']);
        });

        it('blocks the primary action until a brief is present and routes the click to aiGenerate', () => {
            fixture.componentRef.setInput('invalidReasons', []);
            fixture.componentRef.setInput('isDisabled', false);
            fixture.componentRef.setInput('isAiMode', true);
            fixture.componentRef.setInput('aiLanguageSupported', true);
            fixture.componentRef.setInput('aiBriefPresent', false);
            fixture.detectChanges();

            const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
            expect(saveButton.disabled).toBe(true);
            expect(comp.aiActionTooltip()).toBe('artemisApp.programmingExercise.generateExercise.briefRequired');

            fixture.componentRef.setInput('aiBriefPresent', true);
            fixture.detectChanges();
            expect(saveButton.disabled).toBe(false);

            const generated: void[] = [];
            comp.aiGenerate.subscribe(() => generated.push(undefined));
            saveButton.click();
            expect(generated).toHaveLength(1);
        });

        it('keeps the primary action disabled with an explanatory tooltip on an unsupported language', () => {
            fixture.componentRef.setInput('invalidReasons', []);
            fixture.componentRef.setInput('isDisabled', false);
            fixture.componentRef.setInput('isAiMode', true);
            fixture.componentRef.setInput('aiBriefPresent', true);
            fixture.componentRef.setInput('aiLanguageSupported', false);
            fixture.detectChanges();

            const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
            expect(saveButton.disabled).toBe(true);
            expect(comp.aiActionTooltip()).toBe('artemisApp.programmingExercise.generateExercise.languageUnsupported');
        });
    });
});

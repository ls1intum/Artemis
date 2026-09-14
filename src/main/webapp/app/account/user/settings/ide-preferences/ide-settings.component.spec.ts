import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { IdeSettingsComponent } from 'app/account/user/settings/ide-preferences/ide-settings.component';
import { IdeSettingsService } from 'app/account/user/settings/ide-preferences/ide-settings.service';

describe('IdeSettingsComponent', () => {
    let component: IdeSettingsComponent;
    let fixture: ComponentFixture<IdeSettingsComponent>;

    const mockIdeSettingsService = {
        loadPredefinedIdes: vi.fn(),
        loadIdePreferences: vi.fn(),
        saveIdePreference: vi.fn(),
        deleteIdePreference: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: IdeSettingsService, useValue: mockIdeSettingsService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(IdeSettingsComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load predefined IDEs and IDE preferences on init', async () => {
        const predefinedIdes = [
            { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' },
            { name: 'IntelliJ', deepLink: 'jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}' },
        ];
        const idePreferences = new Map([[ProgrammingLanguage.JAVA, predefinedIdes[0]]]);
        const loadedIdePreferences = new Map([
            [ProgrammingLanguage.JAVA, predefinedIdes[0]],
            [ProgrammingLanguage.EMPTY, predefinedIdes[0]],
        ]);

        mockIdeSettingsService.loadPredefinedIdes.mockReturnValue(of(predefinedIdes));
        mockIdeSettingsService.loadIdePreferences.mockReturnValue(Promise.resolve(idePreferences));

        expect(component.isLoading()).toBe(true);

        await component.ngOnInit();

        expect(mockIdeSettingsService.loadPredefinedIdes).toHaveBeenCalledOnce();
        expect(mockIdeSettingsService.loadIdePreferences).toHaveBeenCalledOnce();
        expect(component.PREDEFINED_IDE()).toEqual(predefinedIdes);
        expect(component.programmingLanguageToIde()).toEqual(loadedIdePreferences);
        expect(component.assignedProgrammingLanguages()).toEqual([ProgrammingLanguage.JAVA]);
        expect(component.remainingProgrammingLanguages()).toEqual(
            Object.values(ProgrammingLanguage).filter((x) => x !== ProgrammingLanguage.JAVA && x !== ProgrammingLanguage.EMPTY),
        );
        // Rendering is only unblocked after both async loads have completed.
        expect(component.isLoading()).toBe(false);
    });

    it('should add a programming language and update the lists', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };

        mockIdeSettingsService.saveIdePreference.mockReturnValue(of(ide));

        component.addProgrammingLanguage(programmingLanguage);

        expect(mockIdeSettingsService.saveIdePreference).toHaveBeenCalledExactlyOnceWith(programmingLanguage, ide);
        expect(component.programmingLanguageToIde().get(programmingLanguage)).toEqual(ide);
        expect(component.assignedProgrammingLanguages()).toContain(programmingLanguage);
        expect(component.remainingProgrammingLanguages()).not.toContain(programmingLanguage);
    });

    it('should change the IDE for a programming language', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const intelliJ = { name: 'IntelliJ', deepLink: 'intellij://open?url={cloneUrl}' };

        const idePreferences = new Map([[programmingLanguage, { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }]]);
        component.programmingLanguageToIde.set(idePreferences);

        // Clear any previous calls that may have been triggered by the signal update
        mockIdeSettingsService.saveIdePreference.mockClear();
        mockIdeSettingsService.saveIdePreference.mockReturnValue(of(intelliJ));

        component.changeIde(programmingLanguage, intelliJ);

        expect(mockIdeSettingsService.saveIdePreference).toHaveBeenCalledExactlyOnceWith(programmingLanguage, intelliJ);
        expect(component.programmingLanguageToIde().get(programmingLanguage)).toEqual(intelliJ);
    });

    it('should remove a programming language and update the lists', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const idePreferences = new Map([[programmingLanguage, { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }]]);
        component.programmingLanguageToIde.set(idePreferences);
        component.assignedProgrammingLanguages.set([programmingLanguage]);
        component.remainingProgrammingLanguages.set([]);

        mockIdeSettingsService.deleteIdePreference.mockReturnValue(of(null));

        component.removeProgrammingLanguage(programmingLanguage);

        expect(mockIdeSettingsService.deleteIdePreference).toHaveBeenCalledExactlyOnceWith(programmingLanguage);
        expect(component.programmingLanguageToIde().size).toBe(0);
        expect(component.assignedProgrammingLanguages()).not.toContain(programmingLanguage);
        expect(component.remainingProgrammingLanguages()).toContain(programmingLanguage);
    });

    it('should resolve a selection made by deep link, because option objects are separate instances', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const saved = { name: 'IntelliJ', deepLink: 'jetbrains://idea/checkout/git?checkout.repo={cloneUrl}' };
        component.PREDEFINED_IDE.set([{ name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }, saved]);
        mockIdeSettingsService.saveIdePreference.mockClear();
        mockIdeSettingsService.saveIdePreference.mockReturnValue(of(saved));

        component.changeIdeByDeepLink(programmingLanguage, saved.deepLink);

        expect(mockIdeSettingsService.saveIdePreference).toHaveBeenCalledExactlyOnceWith(programmingLanguage, saved);
        expect(component.programmingLanguageToIde().get(programmingLanguage)).toBe(saved);
    });

    it('should ignore a deep link that matches no known IDE', () => {
        mockIdeSettingsService.saveIdePreference.mockClear();

        component.changeIdeByDeepLink(ProgrammingLanguage.JAVA, 'unknown://nothing');

        expect(mockIdeSettingsService.saveIdePreference).not.toHaveBeenCalled();
    });

    it('should restore the saved selection when the save fails', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const saved = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
        component.programmingLanguageToIde.set(new Map([[programmingLanguage, saved]]));
        const before = component.programmingLanguageToIde();
        mockIdeSettingsService.saveIdePreference.mockClear();
        mockIdeSettingsService.saveIdePreference.mockReturnValue(throwError(() => new Error('save failed')));

        component.changeIde(programmingLanguage, { name: 'CLion', deepLink: 'jetbrains://clion/checkout/git?checkout.repo={cloneUrl}' });

        // The control is fed a one-way value, so the map must change for the selection to snap back.
        expect(component.programmingLanguageToIde()).not.toBe(before);
        expect(component.programmingLanguageToIde().get(programmingLanguage)).toBe(saved);
    });
});

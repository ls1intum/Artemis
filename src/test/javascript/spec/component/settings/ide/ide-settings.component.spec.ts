import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { IdeSettingsComponent } from 'app/shared/user-settings/ide-preferences/ide-settings.component';
import { IdeSettingsService } from 'app/shared/user-settings/ide-preferences/ide-settings.service';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('IdeSettingsComponent', () => {
    let component: IdeSettingsComponent;
    let fixture: ComponentFixture<IdeSettingsComponent>;

    const mockIdeSettingsService = {
        loadPredefinedIdes: jest.fn(),
        loadIdePreferences: jest.fn(),
        saveIdePreference: jest.fn(),
        deleteIdePreference: jest.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [],
            declarations: [IdeSettingsComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: IdeSettingsService, useValue: mockIdeSettingsService }],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
        }).compileComponents();

        fixture = TestBed.createComponent(IdeSettingsComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should load predefined IDEs and IDE preferences on init', () => {
        const predefinedIdes = [
            { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' },
            { name: 'IntelliJ', deepLink: 'jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}' },
        ];
        const idePreferences = new Map([[ProgrammingLanguage.JAVA, predefinedIdes[0]]]);

        mockIdeSettingsService.loadPredefinedIdes.mockReturnValue(of(predefinedIdes));
        mockIdeSettingsService.loadIdePreferences.mockReturnValue(of(idePreferences));

        component.ngOnInit();

        expect(mockIdeSettingsService.loadPredefinedIdes).toHaveBeenCalledOnce();
        expect(mockIdeSettingsService.loadIdePreferences).toHaveBeenCalledOnce();
        expect(component.PREDEFINED_IDE).toEqual(predefinedIdes);
        expect(component.programmingLanguageToIde()).toEqual(idePreferences);
        expect(component.assignedProgrammingLanguages).toEqual([ProgrammingLanguage.JAVA]);
        expect(component.remainingProgrammingLanguages).toEqual(
            Object.values(ProgrammingLanguage).filter((x) => x !== ProgrammingLanguage.JAVA && x !== ProgrammingLanguage.EMPTY),
        );
    });

    it('should add a programming language and update the lists', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };

        mockIdeSettingsService.saveIdePreference.mockReturnValue(of(ide));

        component.addProgrammingLanguage(programmingLanguage);

        expect(mockIdeSettingsService.saveIdePreference).toHaveBeenCalledExactlyOnceWith(programmingLanguage, ide);
        expect(component.programmingLanguageToIde().get(programmingLanguage)).toEqual(ide);
        expect(component.assignedProgrammingLanguages).toContain(programmingLanguage);
        expect(component.remainingProgrammingLanguages).not.toContain(programmingLanguage);
    });

    it('should change the IDE for a programming language', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const intelliJ = { name: 'IntelliJ', deepLink: 'intellij://open?url={cloneUrl}' };

        const idePreferences = new Map([[programmingLanguage, { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }]]);
        component.programmingLanguageToIde.set(idePreferences);

        mockIdeSettingsService.saveIdePreference.mockReturnValue(of(intelliJ));

        component.changeIde(programmingLanguage, intelliJ);

        expect(mockIdeSettingsService.saveIdePreference).toHaveBeenCalledExactlyOnceWith(programmingLanguage, intelliJ);
        expect(component.programmingLanguageToIde().get(programmingLanguage)).toEqual(intelliJ);
    });

    it('should remove a programming language and update the lists', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const idePreferences = new Map([[programmingLanguage, { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' }]]);
        component.programmingLanguageToIde.set(idePreferences);
        component.assignedProgrammingLanguages = [programmingLanguage];
        component.remainingProgrammingLanguages = [];

        mockIdeSettingsService.deleteIdePreference.mockReturnValue(of(null));

        component.removeProgrammingLanguage(programmingLanguage);

        expect(mockIdeSettingsService.deleteIdePreference).toHaveBeenCalledExactlyOnceWith(programmingLanguage);
        expect(component.programmingLanguageToIde().size).toBe(0);
        expect(component.assignedProgrammingLanguages).not.toContain(programmingLanguage);
        expect(component.remainingProgrammingLanguages).toContain(programmingLanguage);
    });

    it('should check if the IDE is assigned to a programming language', () => {
        const programmingLanguage = ProgrammingLanguage.JAVA;
        const ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
        const idePreferences = new Map([[programmingLanguage, ide]]);
        component.programmingLanguageToIde.set(idePreferences);

        expect(component.programmingLanguageToIde().get(programmingLanguage)).toBe(ide);
        const result = component.isIdeOfProgrammingLanguage(programmingLanguage, ide);

        expect(result).toBeTrue();
    });
});

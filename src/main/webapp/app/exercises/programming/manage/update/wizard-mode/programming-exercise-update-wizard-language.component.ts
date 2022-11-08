import { Component, Input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-language',
    templateUrl: './programming-exercise-update-wizard-language.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardLanguageComponent {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() appNamePatternForSwift: string;
    @Input() modePickerOptions: ModePickerOption<ProjectType>[];
    @Input() withDependencies: boolean;
    @Input() packageNameRequired: boolean;
    @Input() packageNamePattern: string;

    @Input() supportsJava: boolean;
    @Input() supportsPython: boolean;
    @Input() supportsC: boolean;
    @Input() supportsHaskell: boolean;
    @Input() supportsKotlin: boolean;
    @Input() supportsVHDL: boolean;
    @Input() supportsAssembler: boolean;
    @Input() supportsSwift: boolean;
    @Input() supportsOCaml: boolean;
    @Input() supportsEmpty: boolean;

    @Input() selectedProgrammingLanguage: ProgrammingLanguage;
    @Input() onProgrammingLanguageChange: (language: ProgrammingLanguage) => ProgrammingLanguage;
    @Input() projectTypes: ProjectType[];
    @Input() selectedProjectType: ProjectType;
    @Input() onProjectTypeChange: (projectType: ProjectType) => ProjectType;
}

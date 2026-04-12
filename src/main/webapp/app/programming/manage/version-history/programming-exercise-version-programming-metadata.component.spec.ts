import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ProgrammingExerciseVersionProgrammingMetadataComponent } from 'app/programming/manage/version-history/programming-exercise-version-programming-metadata.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ActivatedRoute } from '@angular/router';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { vi } from 'vitest';

describe('ProgrammingExerciseVersionProgrammingMetadataComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseVersionProgrammingMetadataComponent>;
    let component: ProgrammingExerciseVersionProgrammingMetadataComponent;

    const courseRoute = {
        snapshot: { params: { courseId: '1', exerciseId: '42' } },
        parent: null,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseVersionProgrammingMetadataComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: courseRoute },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseVersionProgrammingMetadataComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            projectType: 'PLAIN_GRADLE',
            packageName: 'de.test',
            projectKey: 'PROG',
            allowOfflineIde: true,
            staticCodeAnalysisEnabled: true,
            testsCommitId: 'abc12345deadbeef',
            templateParticipation: { id: 10, repositoryUri: 'https://repo/template', commitId: 'tmpl1234abcd5678' },
            solutionParticipation: { id: 11, repositoryUri: 'https://repo/solution', commitId: 'sol12345abcd5678' },
            testRepositoryUri: 'https://repo/tests',
        });
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render programming-specific metadata', () => {
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('JAVA');
        expect(text).toContain('PLAIN_GRADLE');
        expect(text).toContain('de.test');
        expect(text).toContain('PROG');
    });

    it('should pre-compute commit link for course route in repositoryFields', () => {
        const commitField = component.repositoryFields().find((f) => f.kind === 'commit' && f.fullCommitHash === 'abc12345deadbeef');
        expect(commitField).toBeDefined();
        expect(commitField!.commitLink).toEqual(['/course-management', 1, 'programming-exercises', 42, 'repository', RepositoryType.TESTS, 'commit-history', 'abc12345deadbeef']);
    });

    it('should pre-compute repository view link for course route in repositoryFields', () => {
        const repoField = component.repositoryFields().find((f) => f.kind === 'repository' && f.repositoryUri === 'https://repo/template');
        expect(repoField).toBeDefined();
        expect(repoField!.repositoryViewLink).toEqual(['/course-management', 1, 'programming-exercises', 42, 'repository', RepositoryType.TEMPLATE]);
    });

    it('should pre-compute repository editor link for course route in repositoryFields', () => {
        const repoField = component.repositoryFields().find((f) => f.kind === 'repository' && f.repositoryUri === 'https://repo/template');
        expect(repoField).toBeDefined();
        expect(repoField!.repositoryEditorLink).toEqual(['/course-management', 1, 'programming-exercises', 42, 'code-editor', RepositoryType.TEMPLATE, 10]);
    });

    it('should set commitLink to undefined when commit hash is missing', () => {
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            templateParticipation: { commitId: undefined },
        });
        fixture.detectChanges();

        const commitField = component.repositoryFields().find((f) => f.kind === 'commit' && f.label.includes('template'));
        expect(commitField).toBeDefined();
        expect(commitField!.commitLink).toBeUndefined();
    });

    it('should set repositoryEditorLink to undefined when repositoryId is missing', () => {
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            templateParticipation: { repositoryUri: 'https://repo/template' },
        });
        fixture.detectChanges();

        const repoField = component.repositoryFields().find((f) => f.kind === 'repository' && f.repositoryUri === 'https://repo/template');
        expect(repoField).toBeDefined();
        expect(repoField!.repositoryEditorLink).toBeUndefined();
    });

    it('should shorten commit hash to 8 characters in shortCommitHash', () => {
        const commitField = component.repositoryFields().find((f) => f.kind === 'commit' && f.fullCommitHash === 'abc12345deadbeef');
        expect(commitField).toBeDefined();
        expect(commitField!.shortCommitHash).toBe('abc12345');
    });

    it('should set shortCommitHash to dash for missing commit hash', () => {
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            templateParticipation: { commitId: undefined },
        });
        fixture.detectChanges();

        const commitField = component.repositoryFields().find((f) => f.kind === 'commit' && f.label.includes('template'));
        expect(commitField).toBeDefined();
        expect(commitField!.shortCommitHash).toBe('-');
    });

    it('should use fallback label when translation key is not found', () => {
        const branchField = component.buildConfigurationFields().find((f) => f.label === 'Branch');
        expect(branchField).toBeDefined();
    });

    it('should show dash placeholder for missing values', () => {
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            projectType: undefined,
        });
        fixture.detectChanges();

        const emptyField = component.languageFields().find((f) => f.label.includes('projectType'));
        expect(emptyField).toBeDefined();
        expect(emptyField!.currentEmpty).toBeTruthy();
        expect(emptyField!.currentDisplay).toBe('-');

        const javaField = component.languageFields().find((f) => f.currentDisplay === 'JAVA');
        expect(javaField).toBeDefined();
        expect(javaField!.currentEmpty).toBeFalsy();
    });

    it('should detect empty values correctly via currentEmpty', () => {
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            projectType: undefined,
            packageName: '',
        });
        fixture.detectChanges();

        const fields = component.languageFields();
        const langField = fields.find((f) => f.currentDisplay === 'JAVA');
        expect(langField!.currentEmpty).toBeFalsy();

        const projectTypeField = fields.find((f) => f.label.includes('projectType'));
        expect(projectTypeField!.currentEmpty).toBeTruthy();
    });

    it('should hide the language section in diff mode when no language fields changed', () => {
        fixture.componentRef.setInput('previousProgrammingData', {
            programmingLanguage: 'JAVA',
            projectType: 'PLAIN_GRADLE',
            packageName: 'de.test',
            projectKey: 'PROG',
            allowOfflineIde: true,
            staticCodeAnalysisEnabled: true,
            testsCommitId: 'changed-commit',
            templateParticipation: { id: 10, repositoryUri: 'https://repo/template', commitId: 'tmpl5678abcd1234' },
            solutionParticipation: { id: 11, repositoryUri: 'https://repo/solution', commitId: 'sol5678abcd1234' },
            testRepositoryUri: 'https://repo/tests',
        });
        fixture.componentRef.setInput('viewMode', 'changes');
        fixture.detectChanges();

        expect(component.languageFields()).toHaveLength(0);
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.programmingExercise.wizardMode.detailedSteps.languageStepTitle');
    });

    it('should not render task or test case sections', () => {
        const text = fixture.nativeElement.textContent;
        expect(text).not.toContain('artemisApp.programmingExercise.versionHistory.snapshot.tasks');
        expect(text).not.toContain('artemisApp.programmingExercise.versionHistory.snapshot.testCases');
    });
});

describe('ProgrammingExerciseVersionProgrammingMetadataComponent (exam route)', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseVersionProgrammingMetadataComponent>;
    let component: ProgrammingExerciseVersionProgrammingMetadataComponent;

    beforeEach(async () => {
        const examRoute = {
            snapshot: { params: { courseId: '1', exerciseId: '42', examId: '5', exerciseGroupId: '3' } },
            parent: null,
        };

        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseVersionProgrammingMetadataComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: examRoute },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseVersionProgrammingMetadataComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            testsCommitId: 'abc12345deadbeef',
        });
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should pre-compute commit link for exam route', () => {
        const commitField = component.repositoryFields().find((f) => f.kind === 'commit' && f.fullCommitHash === 'abc12345deadbeef');
        expect(commitField).toBeDefined();
        expect(commitField!.commitLink).toEqual([
            '/course-management',
            1,
            'exams',
            5,
            'exercise-groups',
            3,
            'programming-exercises',
            42,
            'repository',
            RepositoryType.TESTS,
            'commit-history',
            'abc12345deadbeef',
        ]);
    });

    it('should pre-compute repository view link for exam route', () => {
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            templateParticipation: { repositoryUri: 'https://repo/template' },
        });
        fixture.detectChanges();

        const repoField = component.repositoryFields().find((f) => f.kind === 'repository' && f.repositoryUri === 'https://repo/template');
        expect(repoField).toBeDefined();
        expect(repoField!.repositoryViewLink).toEqual([
            '/course-management',
            1,
            'exams',
            5,
            'exercise-groups',
            3,
            'programming-exercises',
            42,
            'repository',
            RepositoryType.TEMPLATE,
        ]);
    });

    it('should pre-compute repository editor link for exam route', () => {
        fixture.componentRef.setInput('programmingData', {
            programmingLanguage: 'JAVA',
            solutionParticipation: { id: 11, repositoryUri: 'https://repo/sol' },
        });
        fixture.detectChanges();

        const repoField = component.repositoryFields().find((f) => f.kind === 'repository' && f.repositoryUri === 'https://repo/sol');
        expect(repoField).toBeDefined();
        expect(repoField!.repositoryEditorLink).toEqual([
            '/course-management',
            1,
            'exams',
            5,
            'exercise-groups',
            3,
            'programming-exercises',
            42,
            'code-editor',
            RepositoryType.SOLUTION,
            11,
        ]);
    });
});

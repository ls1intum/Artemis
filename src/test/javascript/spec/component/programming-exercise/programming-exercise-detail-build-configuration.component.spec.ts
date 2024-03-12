import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ElementRef, NgZone } from '@angular/core';
import { ThemeService } from 'app/core/theme/theme.service';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseDetailBuildConfigurationComponent } from 'app/exercises/programming/manage/programming-exercise-detail-build-configuration.component';
import { Course } from 'app/entities/course.model';

describe('ProgrammingExercise Detail Build Configuration', () => {
    let mockThemeService: ThemeService;
    let comp: ProgrammingExerciseDetailBuildConfigurationComponent;
    const course = { id: 123 } as Course;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
    let programmingExercise = new ProgrammingExercise(course, undefined);

    beforeEach(() => {
        programmingExercise = new ProgrammingExercise(course, undefined);
        programmingExercise.customizeBuildPlanWithAeolus = true;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseDetailBuildConfigurationComponent, MockComponent(AceEditorComponent)],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        })
            .compileComponents()
            .then(() => {
                mockThemeService = TestBed.inject(ThemeService);
            });

        const fixture = TestBed.createComponent(ProgrammingExerciseDetailBuildConfigurationComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should accept editor', () => {
        const elementRef: ElementRef = new ElementRef(document.createElement('div'));
        const zone: NgZone = new NgZone({});
        expect(comp.editor).toBeUndefined();
        comp.editor = new AceEditorComponent(elementRef, zone, mockThemeService);
        expect(comp.editor).toBeDefined();
    });

    it('should not fail if setting up undefined editor', () => {
        comp.setupEditor();
        expect(comp.editor).toBeUndefined();
    });

    it('should display correct code in editor', () => {
        const code = 'echo "Hello World"';
        comp.script = code;
        const elementRef: ElementRef = new ElementRef(document.createElement('div'));
        const zone: NgZone = new NgZone({});
        expect(comp.editor).toBeUndefined();
        comp.editor = new AceEditorComponent(elementRef, zone, mockThemeService);
        expect(comp.editor).toBeDefined();
        expect(comp.editor.text).toBe(code);
    });
});

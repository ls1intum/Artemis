import { Course } from 'app/core/course/shared/entities/course.model';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApollonEditor, UMLDiagramType, UMLModel, importDiagram } from '@tumaet/apollon';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import * as testClassDiagram from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import { cloneDeep } from 'lodash-es';
import { SimpleChange } from '@angular/core';
import { MockComponent } from 'ng-mocks';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ModelingEditorComponent', () => {
    let fixture: ComponentFixture<ModelingEditorComponent>;
    let component: ModelingEditorComponent;
    const course = { id: 123 } as Course;
    const diagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
    // @ts-ignore
    const classDiagram = cloneDeep(importDiagram({ id: 'diagram', title: 'Diagram', model: testClassDiagram } as { id: string; title: string; model: UMLModel })); // note: clone is needed to prevent weird errors with setters, because testClassDiagram is not an actual object
    const route = { params: of({ id: 1, courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
    beforeEach(() => {
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(classDiagram);

        TestBed.configureTestingModule({
            declarations: [ModelingEditorComponent, MockComponent(ModelingExplanationEditorComponent)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingEditorComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngAfterViewInit', async () => {
        jest.spyOn(console, 'error').mockImplementation(); // prevent: findDOMNode is deprecated and will be removed in the next major release
        component.umlModel = classDiagram;
        fixture.detectChanges();

        // test
        await component.ngAfterViewInit();
        const editor: ApollonEditor = component['apollonEditor'] as ApollonEditor;
        // Check that editor exists
        expect(editor).toBeDefined();
        await (editor as any).nextRender;

        expect(editor.model.nodes).toHaveLength(classDiagram.nodes.length);
        expect(editor.model.edges).toHaveLength(classDiagram.edges.length);
    });

    it('ngOnDestroy', () => {
        component.umlModel = classDiagram;
        fixture.detectChanges();
        component.ngAfterViewInit();

        component.ngOnDestroy();
        // verify teardown
        expect(component['apollonEditor']).toBeUndefined();
    });

    it('ngOnChanges', async () => {
        // @ts-ignore
        const model = classDiagram;
        component.umlModel = model;
        fixture.detectChanges();
        await component.ngAfterViewInit();

        const changedModel = cloneDeep(model) as any;
        changedModel.nodes = [];
        changedModel.edges = [];
        changedModel.assessments = {};
        // test
        await (component.apollonEditor as any)?.nextRender;
        component.ngOnChanges({
            umlModel: {
                currentValue: changedModel,
                previousValue: model,
            } as SimpleChange,
        });
        await (component.apollonEditor as any)?.nextRender;
        expect(component.umlModel).toEqual(changedModel);
        expect(component['apollonEditor']!.model.assessments).toEqual({});
    });

    it('isFullScreen false', () => {
        // test
        const fullScreen = component.isFullScreen;
        expect(fullScreen).toBeFalse();
    });

    it('getCurrentModel', () => {
        component.umlModel = classDiagram;
        fixture.detectChanges();
        component.ngAfterViewInit();

        // test
        // const model = component.getCurrentModel();
        // TODO: uncomment after deserialization bugfix in Apollon library, see https://github.com/ls1intum/Apollon/issues/146
        // expect(model).toEqual(testClassDiagram);
    });

    it('should not show save indicator without savedStatus set', () => {
        component.savedStatus = undefined;
        component.readOnly = true;
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in read only mode', () => {
        component.savedStatus = { isSaving: false, isChanged: false };
        component.readOnly = true;
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should not show save indicator in fullscreen mode', () => {
        component.savedStatus = { isSaving: false, isChanged: false };
        jest.spyOn(component, 'isFullScreen', 'get').mockReturnValue(true);
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint'));
        expect(statusHint).toBeNull();
    });

    it('should show green checkmark save indicator if everything is saved', () => {
        component.savedStatus = { isSaving: false, isChanged: false };
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-success'));
        expect(statusHint).not.toBeNull();

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();

        const spanElement = statusHint.query(By.css('span')).nativeElement;
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.modelingEditor.allSaved');
    });

    it('should show yellow times save indicator if something is unsaved', () => {
        component.savedStatus = { isSaving: false, isChanged: true };
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-warning'));
        expect(statusHint).not.toBeNull();

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();

        const spanElement = statusHint.query(By.css('span')).nativeElement;
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.modelingEditor.unsavedChanges');
    });

    it('should show saving indicator if it is currently saving', () => {
        component.savedStatus = { isSaving: true, isChanged: true };
        fixture.detectChanges();
        component.ngAfterViewInit();

        const statusHint = fixture.debugElement.query(By.css('.status-hint.text-info'));
        expect(statusHint).not.toBeNull();

        const icon = statusHint.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();

        const spanElement = statusHint.query(By.css('span')).nativeElement;
        expect(spanElement.getAttribute('jhiTranslate')).toBe('artemisApp.modelingEditor.saving');
    });

    it('should handle explanation input change', () => {
        const spy = jest.spyOn(component.explanationChange, 'emit');

        const newExplanation = 'New Explanation';
        component.onExplanationInput(newExplanation);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(newExplanation);
        expect(component.explanation).toBe(newExplanation);
    });

    it('should subscribe to broadcast messages and emit them.', () => {
        fixture.detectChanges();

        const receiver = jest.fn();

        component.onModelPatch.subscribe(receiver);
        const callbacks: Array<(message: string) => void> = [];

        jest.spyOn(ApollonEditor.prototype, 'sendBroadcastMessage').mockImplementation((cb) => {
            callbacks.push(cb);
        });

        component.ngAfterViewInit();

        expect(callbacks).toHaveLength(1);
        callbacks[0]('encoded-patch');
        expect(receiver).toHaveBeenCalledWith('encoded-patch');
    });
});

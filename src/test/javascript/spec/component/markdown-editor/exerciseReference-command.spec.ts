import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseReferenceCommand } from 'app/shared/markdown-editor/commands/exerciseReference.command';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../helpers/mocks/service/mock-metis-service.service';
import { metisExercise } from '../../helpers/sample/metis-sample-data';
import { ValueItem } from 'app/shared/markdown-editor/command-constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';

describe('Exercise Reference Command', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let exerciseReferenceCommand: ExerciseReferenceCommand;
    let metisService: MetisService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, ArtemisMarkdownEditorModule],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [MockComponent(FaIconComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should insert correct reference link for exercise to markdown editor on execute', () => {
        exerciseReferenceCommand = new ExerciseReferenceCommand(metisService);
        exerciseReferenceCommand.setValues([{ id: metisExercise.id!.toString(), value: metisExercise!.title! } as ValueItem]);

        comp.defaultCommands = [exerciseReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const referenceRouterLinkToExercise = '[' + metisExercise.title + '](/courses/' + metisService.getCourse().id + '/exercises/' + metisExercise.id + ')';
        exerciseReferenceCommand.execute(metisExercise.id!.toString());
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToExercise);
    });
});

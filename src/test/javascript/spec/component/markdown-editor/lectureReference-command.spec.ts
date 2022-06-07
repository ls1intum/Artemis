import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../helpers/mocks/service/mock-metis-service.service';
import { metisLecture } from '../../helpers/sample/metis-sample-data';
import { CourseArtifactType } from 'app/shared/markdown-editor/command-constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';
import { LectureReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/lectureReferenceCommand';

describe('Lecture Reference Command', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let lectureReferenceCommand: LectureReferenceCommand;
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

    it('should initialize correctly', () => {
        lectureReferenceCommand = new LectureReferenceCommand(metisService);
        expect(lectureReferenceCommand.getValues()).toEqual(
            metisService.getCourse().lectures!.map((lecture) => ({
                id: lecture.id!.toString(),
                value: lecture.title!,
                type: CourseArtifactType.LECTURE,
                elements: lecture.attachments?.map((attachment) => ({ id: attachment.id!.toString(), value: attachment.name!, courseArtifactType: CourseArtifactType.ATTACHMENT })),
            })),
        );
    });

    it('should insert correct reference link for lecture to markdown editor on execute', () => {
        lectureReferenceCommand = new LectureReferenceCommand(metisService);

        comp.defaultCommands = [lectureReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const referenceRouterLinkToLecture = '[' + metisLecture.title + '](/courses/' + metisService.getCourse().id + '/lectures/' + metisLecture.id + ')';
        lectureReferenceCommand.execute(metisLecture.id!.toString());
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToLecture);
    });
});

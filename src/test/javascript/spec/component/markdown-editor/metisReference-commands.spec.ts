import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../helpers/mocks/service/mock-metis-service.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';
import { ExerciseReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/exerciseReferenceCommand';
import { metisExercise, metisLecture } from '../../helpers/sample/metis-sample-data';
import { LectureAttachmentReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/lectureAttachmentReferenceCommand';
import { ReferenceType } from 'app/shared/metis/metis.util';

describe('Exercise Lecture Attachment Reference Commands', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let exerciseReferenceCommand: ExerciseReferenceCommand;
    let lectureReferenceCommand: LectureAttachmentReferenceCommand;

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

    it('should initialize exercise reference command correctly', () => {
        exerciseReferenceCommand = new ExerciseReferenceCommand(metisService);
        expect(exerciseReferenceCommand.getValues()).toEqual(
            metisService.getCourse().exercises!.map((exercise) => ({ id: exercise.id!.toString(), value: exercise.title!, type: exercise.type })),
        );
    });

    it('should initialize lecture attachment reference command correctly', () => {
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService);
        expect(lectureReferenceCommand.getValues()).toEqual(
            metisService.getCourse().lectures!.map((lecture) => ({
                id: lecture.id!.toString(),
                value: lecture.title!,
                type: ReferenceType.LECTURE,
                elements: lecture.attachments?.map((attachment) => ({ id: attachment.id!.toString(), value: attachment.name!, courseArtifactType: ReferenceType.ATTACHMENT })),
            })),
        );
    });

    it('should insert correct reference link for exercise to markdown editor on execute', () => {
        exerciseReferenceCommand = new ExerciseReferenceCommand(metisService);

        comp.defaultCommands = [exerciseReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const referenceRouterLinkToExercise = `[${metisExercise.type}]${metisExercise.title}(${metisService.getLinkForExercise(metisExercise.id!.toString())})[/${
            metisExercise.type
        }]`;
        exerciseReferenceCommand.execute(metisExercise.id!.toString());
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToExercise);
    });

    it('should insert correct reference link for lecture to markdown editor on execute', () => {
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService);

        comp.defaultCommands = [lectureReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const referenceRouterLinkToLecture = `[lecture]${metisLecture.title}(${metisService.getLinkForLecture(metisLecture.id!.toString())})[/lecture]`;
        lectureReferenceCommand.execute(metisLecture.id!.toString(), ReferenceType.LECTURE);
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToLecture);
    });

    it('should insert correct reference link for attachment to markdown editor on execute', () => {
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService);

        comp.defaultCommands = [lectureReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const referenceRouterLinkToLecture = `[attachment]${metisLecture.attachments?.first()?.name}(${metisLecture.attachments?.first()?.link})[/attachment]`;
        lectureReferenceCommand.execute(metisLecture.id!.toString(), ReferenceType.ATTACHMENT, metisLecture.attachments?.first()?.id!.toString());
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToLecture);
    });
});

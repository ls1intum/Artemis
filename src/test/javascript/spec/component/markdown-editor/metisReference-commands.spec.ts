import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../helpers/mocks/service/mock-metis-service.service';
import { ExerciseReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/exerciseReferenceCommand';
import { metisExercise, metisLecture, metisLecture2, metisLecture3 } from '../../helpers/sample/metis-sample-data';
import { LectureAttachmentReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/lectureAttachmentReferenceCommand';
import { ReferenceType } from 'app/shared/metis/metis.util';
import { LectureService } from 'app/lecture/lecture.service';
import { MockProvider } from 'ng-mocks';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { Slide } from 'app/entities/lecture-unit/slide.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';

describe('Exercise Lecture Attachment Reference Commands', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let exerciseReferenceCommand: ExerciseReferenceCommand;
    let lectureReferenceCommand: LectureAttachmentReferenceCommand;

    let metisService: MetisService;
    let lectureService: LectureService;
    let findLectureWithDetailsSpy: jest.SpyInstance;
    let findAllLecturesWithDetailsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, ArtemisMarkdownEditorModule],
            providers: [{ provide: MetisService, useClass: MockMetisService }, MockProvider(LectureService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                lectureService = TestBed.inject(LectureService);
                findLectureWithDetailsSpy = jest.spyOn(lectureService, 'findWithDetailsWithSlides');
                findAllLecturesWithDetailsSpy = jest.spyOn(lectureService, 'findAllByCourseIdWithSlides');
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
        const attachmentUnit = new AttachmentUnit();
        attachmentUnit.id = 3;
        const slide = new Slide();
        slide.id = 3;
        slide.slideNumber = 3;
        slide.slideImagePath = 'directory/attachments/slides/Metis-Slide-3.png';
        attachmentUnit.slides?.push(slide);
        metisLecture3.lectureUnits?.push(attachmentUnit);
        const returnValue = of(new HttpResponse({ body: [metisLecture, metisLecture2, metisLecture3], status: 200 }));

        findAllLecturesWithDetailsSpy.mockReturnValue(returnValue);
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService, lectureService);
        expect(findAllLecturesWithDetailsSpy).toHaveBeenCalledOnce();

        expect(lectureReferenceCommand.getValues()).toEqual([
            {
                id: metisLecture.id!.toString(),
                value: metisLecture.title!,
                type: ReferenceType.LECTURE,
                elements: metisLecture.attachments?.map((attachment: any) => ({
                    id: attachment.id!.toString(),
                    value: attachment.name!,
                    courseArtifactType: ReferenceType.ATTACHMENT,
                })),
            },
            {
                id: metisLecture2.id!.toString(),
                value: metisLecture2.title!,
                type: ReferenceType.LECTURE,
                elements: metisLecture2.attachments?.map((attachment: any) => ({
                    id: attachment.id!.toString(),
                    value: attachment.name!,
                    courseArtifactType: ReferenceType.ATTACHMENT,
                })),
                attachmentUnits: metisLecture2.lectureUnits?.map((unit: any) => {
                    return {
                        id: unit.id!.toString(),
                        value: unit.name!,
                        slides: unit.slides
                            ?.map((slide: Slide) => {
                                return {
                                    id: slide.id!.toString(),
                                    slideNumber: slide.slideNumber!,
                                    slideImagePath: slide.slideImagePath!,
                                    courseArtifactType: ReferenceType.SLIDE,
                                };
                            })
                            .sort((a: Slide, b: Slide) => a.slideNumber! - b.slideNumber!),
                        courseArtifactType: ReferenceType.ATTACHMENT_UNITS,
                    };
                }),
            },
            {
                id: metisLecture3.id!.toString(),
                value: metisLecture3.title!,
                type: ReferenceType.LECTURE,
                elements: metisLecture3.attachments?.map((attachment: any) => ({
                    id: attachment.id!.toString(),
                    value: attachment.name!,
                    courseArtifactType: ReferenceType.ATTACHMENT,
                })),
                attachmentUnits: metisLecture3.lectureUnits?.map((unit: any) => {
                    return {
                        id: unit.id!.toString(),
                        value: unit.name!,
                        slides: unit.slides
                            ?.map((slide: Slide) => {
                                return {
                                    id: slide.id!.toString(),
                                    slideNumber: slide.slideNumber!,
                                    slideImagePath: slide.slideImagePath!,
                                    courseArtifactType: ReferenceType.SLIDE,
                                };
                            })
                            .sort((a: Slide, b: Slide) => a.slideNumber! - b.slideNumber!),
                        courseArtifactType: ReferenceType.ATTACHMENT_UNITS,
                    };
                }),
            },
        ]);
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
        const returnValue = of(new HttpResponse({ body: [metisLecture], status: 200 }));
        findAllLecturesWithDetailsSpy.mockReturnValue(returnValue);
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService, lectureService);
        expect(findAllLecturesWithDetailsSpy).toHaveBeenCalledOnce();

        const returnValueDetail = of(new HttpResponse({ body: metisLecture, status: 200 }));
        findLectureWithDetailsSpy.mockReturnValue(returnValueDetail);

        comp.defaultCommands = [lectureReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const referenceRouterLinkToLecture = `[lecture]${metisLecture.title}(${metisService.getLinkForLecture(metisLecture.id!.toString())})[/lecture]`;
        lectureReferenceCommand.execute(metisLecture.id!.toString(), ReferenceType.LECTURE);
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToLecture);
    });

    it('should insert correct reference link for attachment to markdown editor on execute', () => {
        const returnValue = of(new HttpResponse({ body: [], status: 200 }));
        findAllLecturesWithDetailsSpy.mockReturnValue(returnValue);
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService, lectureService);
        expect(findAllLecturesWithDetailsSpy).toHaveBeenCalledOnce();

        const returnValueDetail = of(new HttpResponse({ body: {}, status: 200 }));
        findLectureWithDetailsSpy.mockReturnValue(returnValueDetail);

        comp.defaultCommands = [lectureReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const shortLink = metisLecture.attachments?.first() ? metisLecture.attachments?.first()?.link!.split('attachments/')[1] : '';
        const referenceRouterLinkToLecture = `[attachment]${metisLecture.attachments?.first()?.name}(${shortLink})[/attachment]`;
        lectureReferenceCommand.execute(metisLecture.id!.toString(), ReferenceType.ATTACHMENT, metisLecture.attachments?.first()?.id!.toString());
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToLecture);
    });

    it('should insert correct reference link for attachment unit to markdown editor on execute', () => {
        const returnValue = of(new HttpResponse({ body: [metisLecture3], status: 200 }));
        findAllLecturesWithDetailsSpy.mockReturnValue(returnValue);
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService, lectureService);
        expect(findAllLecturesWithDetailsSpy).toHaveBeenCalledOnce();

        const returnValueDetail = of(new HttpResponse({ body: metisLecture3, status: 200 }));
        findLectureWithDetailsSpy.mockReturnValue(returnValueDetail);

        comp.defaultCommands = [lectureReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const selectedUnit: AttachmentUnit = metisLecture3.lectureUnits?.first() as AttachmentUnit;
        const shortLink = selectedUnit?.attachment ? selectedUnit?.attachment?.link!.split('attachments/')[1] : '';
        const referenceRouterLinkToLectureUnit = `[lecture-unit]${metisLecture3.lectureUnits?.first()?.name}(${shortLink})[/lecture-unit]`;
        lectureReferenceCommand.execute(metisLecture3.id!.toString(), ReferenceType.ATTACHMENT_UNITS, undefined, selectedUnit?.id!.toString());
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToLectureUnit);
    });

    it('should insert correct reference link for attachment unit SLIDE to markdown editor on execute', () => {
        const returnValue = of(new HttpResponse({ body: [metisLecture3], status: 200 }));
        findAllLecturesWithDetailsSpy.mockReturnValue(returnValue);
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService, lectureService);
        expect(findAllLecturesWithDetailsSpy).toHaveBeenCalledOnce();

        const returnValueDetail = of(new HttpResponse({ body: metisLecture3, status: 200 }));
        findLectureWithDetailsSpy.mockReturnValue(returnValueDetail);
        lectureReferenceCommand = new LectureAttachmentReferenceCommand(metisService, lectureService);

        comp.defaultCommands = [lectureReferenceCommand];
        fixture.detectChanges();

        comp.aceEditorContainer.getEditor().setValue('');

        const selectedUnit: AttachmentUnit = metisLecture3.lectureUnits?.first() as AttachmentUnit;
        const selectedSlide: Slide = selectedUnit.slides?.first() as Slide;
        const shortLink = selectedSlide.slideImagePath!.split('attachments/')[1];
        // Use a regular expression and the replace() method to remove the file name and last slash
        const shortLinkWithoutFileName: string = shortLink.replace(new RegExp(`[^/]*${'.png'}`), '').replace(/\/$/, '');
        const referenceRouterLinkToSlide = `[slide]${metisLecture3.lectureUnits?.first()?.name} Slide ${selectedSlide.slideNumber}(${shortLinkWithoutFileName})[/slide]`;
        lectureReferenceCommand.execute(metisLecture3.id!.toString(), ReferenceType.ATTACHMENT_UNITS, undefined, selectedUnit?.id!.toString(), selectedSlide?.id!.toString());
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(referenceRouterLinkToSlide);
    });
});

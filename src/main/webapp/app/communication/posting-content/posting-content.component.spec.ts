import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PostingContentPartComponent } from 'app/communication/posting-content/posting-content-part/posting-content-part.components';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { MetisService } from 'app/communication/service/metis.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { PatternMatch, PostingContentPart, ReferenceType } from 'app/communication/metis.util';
import { Observable, of } from 'rxjs';
import { Post } from 'app/communication/shared/entities/post.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { metisCourse, metisCoursePosts, metisExercisePosts, metisGeneralCourseWidePosts, metisLecturePosts } from 'test/helpers/sample/metis-sample-data';
import { Params } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('PostingContentComponent', () => {
    let component: PostingContentComponent;
    let fixture: ComponentFixture<PostingContentComponent>;
    let metisService: MetisService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MetisService, useClass: MockMetisService }],
            declarations: [
                PostingContentComponent,
                MockComponent(PostingContentPartComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingContentComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
            });
    });

    it('should set course and posts for course on initialization', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.currentlyLoadedPosts).toEqual(metisCoursePosts);
    }));

    it('should calculate correct pattern matches for content without reference', () => {
        fixture.componentRef.setInput('content', 'I do not want to reference a Post.');
        expect(component.getPatternMatches()).toEqual([]);
        fixture.componentRef.setInput('content', 'I do not want to reference a Post - #yolo.');
        expect(component.getPatternMatches()).toEqual([]);
        fixture.componentRef.setInput('content', '##I do not want to reference a Post.');
        expect(component.getPatternMatches()).toEqual([]);
        fixture.componentRef.setInput('content', '## 1. do not want to reference a Post.');
        expect(component.getPatternMatches()).toEqual([]);
    });

    it('should calculate correct pattern matches for content with one post reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference #4!');
        const firstMatch = { startIndex: 23, endIndex: 25, referenceType: ReferenceType.POST } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with post references', () => {
        fixture.componentRef.setInput('content', 'I do want to reference #4 and #10 in my posting content.');
        const firstMatch = { startIndex: 23, endIndex: 25, referenceType: ReferenceType.POST } as PatternMatch;
        const secondMatch = { startIndex: 30, endIndex: 33, referenceType: ReferenceType.POST } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch, secondMatch]);
    });

    it('should calculate correct pattern matches for content with one file upload exercise reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [file-upload](courses/1/exercises/1)File Upload Exercise[/file-upload]!');
        const firstMatch = {
            startIndex: 23,
            endIndex: 93,
            referenceType: ReferenceType.FILE_UPLOAD,
        } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with one modeling exercise reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [modeling](courses/1/exercises/1)Modeling Exercise[/modeling]!');
        const firstMatch = { startIndex: 23, endIndex: 84, referenceType: ReferenceType.MODELING } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with one quiz exercise reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [quiz](courses/1/exercises/1)Quiz Exercise[/quiz]!');
        const firstMatch = { startIndex: 23, endIndex: 72, referenceType: ReferenceType.QUIZ } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with one programming exercise reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [programming](courses/1/exercises/1)Programming Exercise[/programming]!');
        const firstMatch = {
            startIndex: 23,
            endIndex: 93,
            referenceType: ReferenceType.PROGRAMMING,
        } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with one text exercise reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [text](courses/1/exercises/1)Text Exercise[/text]!');
        const firstMatch = { startIndex: 23, endIndex: 72, referenceType: ReferenceType.TEXT } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with one lecture reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [lecture](courses/1/lectures/1)Lecture[/lecture]!');
        const firstMatch = { startIndex: 23, endIndex: 71, referenceType: ReferenceType.LECTURE } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with one attachment reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [attachment](attachmentPath/attachment.pdf)PDF File[/attachment]!');
        const firstMatch = {
            startIndex: 23,
            endIndex: 87,
            referenceType: ReferenceType.ATTACHMENT,
        } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with one user reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [user]name(login)[/user]!');
        const firstMatch = { startIndex: 23, endIndex: 47, referenceType: ReferenceType.USER } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with one channel reference', () => {
        fixture.componentRef.setInput('content', 'I do want to reference [channel]test(1)[/channel]!');
        const firstMatch = { startIndex: 23, endIndex: 49, referenceType: ReferenceType.CHANNEL } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should display undo delete prompt when isDeleted is set to true', () => {
        fixture.componentRef.setInput('isDeleted', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.posting-content-undo-delete')).not.toBeNull();
        fixture.componentRef.setInput('isDeleted', false);
    });

    it('should calculate correct pattern matches for content with post, multiple exercise, lecture and attachment references', () => {
        fixture.componentRef.setInput(
            'content',
            'I do want to reference #4, #10, ' +
                '[file-upload](courses/1/exercises/1)File Upload Exercise[/file-upload], ' +
                '[modeling](courses/1/exercises/1)Modeling Exercise[/modeling], ' +
                '[quiz](courses/1/exercises/1)Quiz Exercise[/quiz], ' +
                '[programming](courses/1/exercises/1)Programming Exercise[/programming], ' +
                '[text](courses/1/exercises/1)Text Exercise[/text], ' +
                '[lecture](courses/1/lectures/1)Lecture[/lecture], and ' +
                '[attachment](attachmentPath/attachment.pdf)PDF File[/attachment] in my posting content' +
                '[user]name(login)[/user]! ' +
                'Check [channel]test(1)[/channel], as well!',
        );

        const firstMatch = { startIndex: 23, endIndex: 25, referenceType: ReferenceType.POST } as PatternMatch;
        const secondMatch = { startIndex: 27, endIndex: 30, referenceType: ReferenceType.POST } as PatternMatch;
        const thirdMatch = {
            startIndex: 32,
            endIndex: 102,
            referenceType: ReferenceType.FILE_UPLOAD,
        } as PatternMatch;
        const fourthMatch = {
            startIndex: 104,
            endIndex: 165,
            referenceType: ReferenceType.MODELING,
        } as PatternMatch;
        const fifthMatch = { startIndex: 167, endIndex: 216, referenceType: ReferenceType.QUIZ } as PatternMatch;
        const sixthMatch = {
            startIndex: 218,
            endIndex: 288,
            referenceType: ReferenceType.PROGRAMMING,
        } as PatternMatch;
        const seventhMatch = { startIndex: 290, endIndex: 339, referenceType: ReferenceType.TEXT } as PatternMatch;
        const eightMatch = { startIndex: 341, endIndex: 389, referenceType: ReferenceType.LECTURE } as PatternMatch;
        const ninthMatch = {
            startIndex: 395,
            endIndex: 459,
            referenceType: ReferenceType.ATTACHMENT,
        } as PatternMatch;
        const tenthMatch = { startIndex: 481, endIndex: 505, referenceType: ReferenceType.USER } as PatternMatch;
        const eleventhMath = {
            startIndex: 513,
            endIndex: 539,
            referenceType: ReferenceType.CHANNEL,
        } as PatternMatch;

        expect(component.getPatternMatches()).toEqual([
            firstMatch,
            secondMatch,
            thirdMatch,
            fourthMatch,
            fifthMatch,
            sixthMatch,
            seventhMatch,
            eightMatch,
            ninthMatch,
            tenthMatch,
            eleventhMath,
        ]);
    });

    describe('Computing posting content parts', () => {
        it('should only include content before reference for empty patternMatches', () => {
            fixture.componentRef.setInput('content', 'I do not want to reference a Post.');
            component.computePostingContentParts([]);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: component.content(),
                    linkToReference: undefined,
                    queryParams: undefined,
                    referenceStr: undefined,
                    contentAfterReference: undefined,
                } as unknown as PostingContentPart,
            ]);
        });

        it('should include content before and reference as well as a linked reference within an exercise context', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having an exercise context -> simulating being at an exercise page
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisExercisePosts) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            expect(component.currentlyLoadedPosts).toEqual(metisExercisePosts);
            // in the posting content, use the reference to an id that is included in the lists of currently loaded posts and can therefore be referenced directly,
            // i.e. being shown in the detail view of the discussion section on the current exercise page
            const idOfExercisePostToReference = component.currentlyLoadedPosts[0].id!;
            fixture.componentRef.setInput('content', `I want to reference #${idOfExercisePostToReference} in the same exercise context.`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfExercisePostToReference}` },
                    referenceStr: `#${idOfExercisePostToReference}`,
                    referenceType: ReferenceType.POST,
                    contentAfterReference: ' in the same exercise context.',
                } as unknown as PostingContentPart,
            ]);
        }));

        it('should include content before and reference as well as a linked reference within a lecture context', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a lecture context -> simulating being at a lecture page
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisLecturePosts) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            expect(component.currentlyLoadedPosts).toEqual(metisLecturePosts);
            // in the posting content, use the reference to an id that is included in the lists of currently loaded posts and can therefore be referenced directly,
            // i.e. being shown in the detail view of the discussion section on the current lecture page
            const idOfLecturePostToReference = component.currentlyLoadedPosts[0].id!;
            fixture.componentRef.setInput('content', `I want to reference #${idOfLecturePostToReference} in the same lecture context.`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfLecturePostToReference}` },
                    referenceStr: `#${idOfLecturePostToReference}`,
                    referenceType: ReferenceType.POST,
                    contentAfterReference: ' in the same lecture context.',
                } as PostingContentPart,
            ]);
        }));

        it('should include content before and reference as well as a linked reference within the course discussion overview', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a course-wide context  -> simulating being at course discussion overview
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisGeneralCourseWidePosts) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            expect(component.currentlyLoadedPosts).toEqual(metisGeneralCourseWidePosts);
            // in the posting content, use the reference to an id that is included in the lists of currently loaded posts and can therefore be referenced directly,
            // i.e. being shown in the detail view of the course overview
            const idOfGeneralCourseWidePost = component.currentlyLoadedPosts[0].id!;
            fixture.componentRef.setInput(
                'content',
                `I want to reference #${idOfGeneralCourseWidePost} with course-wide context while currently being at course discussion overview.`,
            );
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfGeneralCourseWidePost}` },
                    referenceStr: `#${idOfGeneralCourseWidePost}`,
                    referenceType: ReferenceType.POST,
                    contentAfterReference: ' with course-wide context while currently being at course discussion overview.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a post from a lecture context while being at the course discussion overview.', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a course-wide context -> simulating being at course discussion overview
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisGeneralCourseWidePosts) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            // in the posting content, use the reference to an id that is _not_ included in the lists of currently loaded posts and can therefore _not_ be referenced directly,
            // and rather being queried for in the course overview
            const idOfLecturePostToReference = metisLecturePosts[0].id!;
            fixture.componentRef.setInput(
                'content',
                `I want to reference #${idOfLecturePostToReference} with lecture context while currently being at the course discussion overview.`,
            );
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfLecturePostToReference}` },
                    referenceStr: `#${idOfLecturePostToReference}`,
                    referenceType: ReferenceType.POST,
                    contentAfterReference: ' with lecture context while currently being at the course discussion overview.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a post from an exercise context while being at the course discussion overview', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a course-wide context -> simulating being at course discussion overview
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisGeneralCourseWidePosts) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            // in the posting content, use the reference to an id that is _not_ included in the lists of currently loaded posts and can therefore _not_ be referenced directly,
            // and rather being queried for in the course overview
            const idOfExercisePostToReference = metisExercisePosts[0].id!;
            fixture.componentRef.setInput(
                'content',
                `I want to reference #${idOfExercisePostToReference} with exercise context while currently being at the course discussion overview.`,
            );
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfExercisePostToReference}` },
                    referenceStr: `#${idOfExercisePostToReference}`,
                    referenceType: ReferenceType.POST,
                    contentAfterReference: ' with exercise context while currently being at the course discussion overview.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a post with course-wide context while being at a lecture page', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a course-wide context -> simulating being at lecture page
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisLecturePosts) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            // in the posting content, use the reference to an id that is _not_ included in the lists of currently loaded posts and can therefore _not_ be referenced directly,
            // and rather being queried for in the course overview
            const idOfCourseWidePostToReference = metisGeneralCourseWidePosts[0].id!;
            fixture.componentRef.setInput('content', `I want to reference #${idOfCourseWidePostToReference} with course-wide context while currently being at a lecture page.`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfCourseWidePostToReference}` },
                    referenceStr: `#${idOfCourseWidePostToReference}`,
                    referenceType: ReferenceType.POST,
                    contentAfterReference: ' with course-wide context while currently being at a lecture page.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a post with lecture context while being at a lecture page', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a course-wide context -> simulating being at lecture page
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisLecturePosts) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            // in the posting content, use the reference to an id that is _not_ included in the lists of currently loaded posts and can therefore _not_ be referenced directly,
            // and rather being queried for in the course overview
            const idOfExercisePostToReference = metisExercisePosts[0].id!;
            fixture.componentRef.setInput('content', `I want to reference #${idOfExercisePostToReference} with exercise context while currently being at a lecture page.`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfExercisePostToReference}` },
                    referenceStr: `#${idOfExercisePostToReference}`,
                    referenceType: ReferenceType.POST,
                    contentAfterReference: ' with exercise context while currently being at a lecture page.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a file upload exercise', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [file-upload]File Upload Exercise(courses/1/exercises/1)[/file-upload].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['courses/1/exercises/1'],
                    referenceStr: `File Upload Exercise`,
                    referenceType: ReferenceType.FILE_UPLOAD,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a modeling exercise', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [modeling]Modeling Exercise(courses/1/exercises/1)[/modeling].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['courses/1/exercises/1'],
                    referenceStr: `Modeling Exercise`,
                    referenceType: ReferenceType.MODELING,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a quiz exercise', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [quiz]Quiz Exercise(courses/1/exercises/1)[/quiz].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['courses/1/exercises/1'],
                    referenceStr: `Quiz Exercise`,
                    referenceType: ReferenceType.QUIZ,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a programming exercise', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [programming]Programming Exercise(courses/1/exercises/1)[/programming].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['courses/1/exercises/1'],
                    referenceStr: `Programming Exercise`,
                    referenceType: ReferenceType.PROGRAMMING,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a text exercise', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [text]Text Exercise(courses/1/exercises/1)[/text].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['courses/1/exercises/1'],
                    referenceStr: `Text Exercise`,
                    referenceType: ReferenceType.TEXT,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a lecture', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [lecture]Lecture 1(courses/1/lectures/1)[/lecture].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['courses/1/lectures/1'],
                    referenceStr: `Lecture 1`,
                    referenceType: ReferenceType.LECTURE,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing an attachment', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [attachment]PDF File(attachmentPath/attachment.pdf)[/attachment].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            // the attachment directory that is removed when showing the text in edit mode
            const attachmentDirectory = 'api/core/files/attachments/';
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    referenceStr: `PDF File`,
                    referenceType: ReferenceType.ATTACHMENT,
                    attachmentToReference: attachmentDirectory + 'attachmentPath/attachment.pdf',
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing an lecture unit', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [lecture-unit]PDF File lecture unit(attachmentPath/attachmentUnit.pdf)[/lecture-unit].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            // the attachment directory that is removed when showing the text in edit mode
            const attachmentDirectory = 'api/core/files/attachments/';
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    referenceStr: `PDF File lecture unit`,
                    referenceType: ReferenceType.ATTACHMENT_UNITS,
                    attachmentToReference: attachmentDirectory + 'attachmentPath/attachmentUnit.pdf',
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a single slide', fakeAsync(() => {
            fixture.componentRef.setInput('content', `I want to reference [slide]PDF File Slide 7(slides/attachment-unit/123/slide/9)[/slide].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            // the attachment directory that is removed when showing the text in edit mode
            const attachmentDirectory = 'api/core/files/attachments/';
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    referenceStr: `PDF File Slide 7`,
                    referenceType: ReferenceType.SLIDE,
                    slideToReference: attachmentDirectory + 'slides/attachment-unit/123/slide/9',
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a user', fakeAsync(() => {
            fixture.componentRef.setInput('content', `This message is important for [user]Test(test_login)[/user].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'This message is important for ',
                    referenceStr: `Test`,
                    referenceType: ReferenceType.USER,
                    queryParams: { referenceUserLogin: 'test_login' } as Params,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a faq', () => {
            fixture.componentRef.setInput('content', `I want to reference [faq]faq(/courses/1/faq?faqId=45)[/faq].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses/1/faq'],
                    referenceStr: `faq`,
                    referenceType: ReferenceType.FAQ,
                    contentAfterReference: '.',
                    queryParams: { faqId: '45' },
                } as PostingContentPart,
            ]);
        });

        it('should compute parts when referencing a channel', fakeAsync(() => {
            fixture.componentRef.setInput('content', `This topic belongs to [channel]test(1)[/channel].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'This topic belongs to ',
                    referenceStr: 'test',
                    referenceType: ReferenceType.CHANNEL,
                    queryParams: { channelId: 1 } as Params,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));

        it('should set channelID undefined if referenced a channel id is not a number', fakeAsync(() => {
            fixture.componentRef.setInput('content', `This topic belongs to [channel]test(abc)[/channel].`);
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts()).toEqual([
                {
                    contentBeforeReference: 'This topic belongs to ',
                    referenceStr: 'test',
                    referenceType: ReferenceType.CHANNEL,
                    queryParams: { channelId: undefined } as Params,
                    contentAfterReference: '.',
                } as PostingContentPart,
            ]);
        }));
    });
});

import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { PostingContentPartComponent } from 'app/shared/metis/posting-content/posting-content-part/posting-content-part.components';
import { MockComponent, MockPipe } from 'ng-mocks';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { MetisService } from 'app/shared/metis/metis.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { PatternMatch, PostingContentPart } from 'app/shared/metis/metis.util';
import { Observable, of } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    metisCourse,
    metisCoursePosts,
    metisCoursePostsWithCourseWideContext,
    metisExercise,
    metisExercisePosts,
    metisLecture,
    metisLecturePosts,
} from '../../../../helpers/sample/metis-sample-data';

describe('PostingContentComponent', () => {
    let component: PostingContentComponent;
    let fixture: ComponentFixture<PostingContentComponent>;
    let metisService: MetisService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [PostingContentComponent, MockComponent(PostingContentPartComponent), MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingContentComponent);
                component = fixture.componentInstance;
                metisService = fixture.debugElement.injector.get(MetisService);
            });
    });

    it('should set course and posts for course on initialization', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.currentlyLoadedPosts).toEqual(metisCoursePosts);
    }));

    it('should calculate correct pattern matches for content without reference', () => {
        component.content = 'I do not want to reference a Post.';
        expect(component.getPatternMatches()).toEqual([]);
    });

    it('should calculate correct pattern matches for content without reference.', () => {
        component.content = 'I do not want to reference a Post - #yolo.';
        expect(component.getPatternMatches()).toEqual([]);
    });

    it('should calculate correct pattern matches for content without reference.', () => {
        component.content = '##I do not want to reference a Post.';
        expect(component.getPatternMatches()).toEqual([]);
    });

    it('should calculate correct pattern matches for content without reference.', () => {
        component.content = '## 1. do not want to reference a Post.';
        expect(component.getPatternMatches()).toEqual([]);
    });

    it('should calculate correct pattern matches for content with one reference', () => {
        component.content = 'I do want to reference #4!';
        const firstMatch = { startIndex: 23, endIndex: 25 } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch]);
    });

    it('should calculate correct pattern matches for content with references', () => {
        component.content = 'I do want to reference #4 and #10 in my posting content.';
        const firstMatch = { startIndex: 23, endIndex: 25 } as PatternMatch;
        const secondMatch = { startIndex: 30, endIndex: 33 } as PatternMatch;
        expect(component.getPatternMatches()).toEqual([firstMatch, secondMatch]);
    });

    describe('Computing posting content parts', () => {
        it('should only include content before reference for empty patternMatches', () => {
            component.content = 'I do not want to reference a Post.';
            component.computePostingContentParts([]);
            expect(component.postingContentParts).toEqual([
                {
                    contentBeforeReference: component.content,
                    linkToReference: undefined,
                    queryParams: undefined,
                    referenceStr: undefined,
                    contentAfterReference: undefined,
                } as PostingContentPart,
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
            component.content = `I want to reference #${idOfExercisePostToReference} in the same exercise context.`;
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'exercises', metisExercise.id],
                    queryParams: { postId: idOfExercisePostToReference },
                    referenceStr: `#${idOfExercisePostToReference}`,
                    contentAfterReference: ' in the same exercise context.',
                } as PostingContentPart,
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
            component.content = `I want to reference #${idOfLecturePostToReference} in the same lecture context.`;
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'lectures', metisLecture.id],
                    queryParams: { postId: idOfLecturePostToReference },
                    referenceStr: `#${idOfLecturePostToReference}`,
                    contentAfterReference: ' in the same lecture context.',
                } as PostingContentPart,
            ]);
        }));

        it('should include content before and reference as well as a linked reference within the course discussion overview', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a course-wide context  -> simulating being at course discussion overview
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisCoursePostsWithCourseWideContext) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            expect(component.currentlyLoadedPosts).toEqual(metisCoursePostsWithCourseWideContext);
            // in the posting content, use the reference to an id that is included in the lists of currently loaded posts and can therefore be referenced directly,
            // i.e. being shown in the detail view of the course overview
            const idOfPostWithCourseWideContextToReference = component.currentlyLoadedPosts[0].id!;
            component.content = `I want to reference #${idOfPostWithCourseWideContextToReference} with course-wide context while currently being at course discussion overview.`;
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfPostWithCourseWideContextToReference}` },
                    referenceStr: `#${idOfPostWithCourseWideContextToReference}`,
                    contentAfterReference: ' with course-wide context while currently being at course discussion overview.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a post from a lecture context while being at the course discussion overview.', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a course-wide context -> simulating being at course discussion overview
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisCoursePostsWithCourseWideContext) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            // in the posting content, use the reference to an id that is _not_ included in the lists of currently loaded posts and can therefore _not_ be referenced directly,
            // and rather being queried for in the course overview
            const idOfLecturePostToReference = metisLecturePosts[0].id!;
            component.content = `I want to reference #${idOfLecturePostToReference} with lecture context while currently being at the course discussion overview.`;
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfLecturePostToReference}` },
                    referenceStr: `#${idOfLecturePostToReference}`,
                    contentAfterReference: ' with lecture context while currently being at the course discussion overview.',
                } as PostingContentPart,
            ]);
        }));

        it('should compute parts when referencing a post from an exercise context while being at the course discussion overview', fakeAsync(() => {
            // currently loaded posts will be set to a list of posts having a course-wide context -> simulating being at course discussion overview
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(of(metisCoursePostsWithCourseWideContext) as Observable<Post[]>);
            component.ngOnInit();
            tick();
            // in the posting content, use the reference to an id that is _not_ included in the lists of currently loaded posts and can therefore _not_ be referenced directly,
            // and rather being queried for in the course overview
            const idOfExercisePostToReference = metisExercisePosts[0].id!;
            component.content = `I want to reference #${idOfExercisePostToReference} with exercise context while currently being at the course discussion overview.`;
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfExercisePostToReference}` },
                    referenceStr: `#${idOfExercisePostToReference}`,
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
            const idOfPostWithCourseWideContextToReference = metisCoursePostsWithCourseWideContext[0].id!;
            component.content = `I want to reference #${idOfPostWithCourseWideContextToReference} with course-wide context while currently being at a lecture page.`;
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfPostWithCourseWideContextToReference}` },
                    referenceStr: `#${idOfPostWithCourseWideContextToReference}`,
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
            component.content = `I want to reference #${idOfExercisePostToReference} with exercise context while currently being at a lecture page.`;
            const matches = component.getPatternMatches();
            component.computePostingContentParts(matches);
            expect(component.postingContentParts).toEqual([
                {
                    contentBeforeReference: 'I want to reference ',
                    linkToReference: ['/courses', metisCourse.id, 'discussion'],
                    queryParams: { searchText: `#${idOfExercisePostToReference}` },
                    referenceStr: `#${idOfExercisePostToReference}`,
                    contentAfterReference: ' with exercise context while currently being at a lecture page.',
                } as PostingContentPart,
            ]);
        }));
    });
});

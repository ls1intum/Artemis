import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { GlobalSearchIrisHandoffComponent } from 'app/core/navbar/global-search/components/views/iris-handoff/global-search-iris-handoff.component';
import { CitedSources } from 'app/core/navbar/global-search/util/iris-cited-sources.util';
import { EntitySearchSource } from 'app/core/navbar/global-search/models/entity-search-source.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { OsDetectorService } from 'app/core/navbar/global-search/services/os-detector.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';

const course = { id: 1, name: 'Introduction to Algorithms' };

function slide(lecture: { id: number; name: string }, unitId: number, page: number): LectureSearchResult {
    return {
        course,
        lecture,
        lectureUnit: {
            id: unitId,
            name: 'Slides',
            link: `/courses/1/lectures/${lecture.id}`,
            pageNumber: page,
            sourceType: 'lecture_unit_slide',
            queryParams: { unit: unitId, page },
        },
    };
}

describe('GlobalSearchIrisHandoffComponent', () => {
    let fixture: ComponentFixture<GlobalSearchIrisHandoffComponent>;
    let createSession: ReturnType<typeof vi.fn>;
    let router: Router;

    const oneLecture: CitedSources = { sources: [slide({ id: 10, name: 'Divide and Conquer' }, 100, 3)], entitySources: [] };
    const twoLectures: CitedSources = {
        sources: [slide({ id: 10, name: 'Divide and Conquer' }, 100, 3), slide({ id: 11, name: 'Recurrences' }, 101, 4), slide({ id: 11, name: 'Recurrences' }, 102, 5)],
        entitySources: [],
    };

    const button = (): HTMLButtonElement | null => fixture.nativeElement.querySelector('[data-testid="iris-handoff-button"]');

    function render(cited: CitedSources, question = '  Why is merge sort O(n log n)?  ', answer = 'It halves the input.[1]') {
        fixture.componentRef.setInput('question', question);
        fixture.componentRef.setInput('answer', answer);
        fixture.componentRef.setInput('citedSources', cited);
        fixture.detectChanges();
    }

    beforeEach(async () => {
        createSession = vi.fn().mockReturnValue(of({ id: 77 } as IrisSession));
        await TestBed.configureTestingModule({
            imports: [GlobalSearchIrisHandoffComponent],
            providers: [
                provideRouter([]),
                { provide: IrisChatHttpService, useValue: { createSessionFromGlobalSearch: createSession } },
                { provide: OsDetectorService, useValue: { actionKeyLabel: signal('⌘'), isActionKey: (event: KeyboardEvent) => event.metaKey } },
            ],
        })
            .overrideComponent(GlobalSearchIrisHandoffComponent, {
                remove: { imports: [ArtemisTranslatePipe] },
                add: { imports: [MockPipe(ArtemisTranslatePipe, (key: string, params?: { name?: string }) => (params?.name ? `${key}: ${params.name}` : key))] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(GlobalSearchIrisHandoffComponent);
        router = TestBed.inject(Router);
        vi.spyOn(router, 'navigate').mockResolvedValue(true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('shows the shortcut and names the lecture the answer comes from in its accessible name', () => {
        render(oneLecture);

        expect(button()?.textContent?.replace(/\s+/g, '')).toBe('global.search.irisHandoffContinue⌘↵');
        expect(button()?.getAttribute('aria-label')).toBe('global.search.irisHandoffContinueIn: Divide and Conquer');
    });

    it('renders nothing when no source names a course', () => {
        render({ sources: [], entitySources: [{ entityType: 'faq', title: 'Without a course' } as EntitySearchSource] });

        expect(button()).toBeNull();
    });

    it('creates the chat with the question and the converted answer, then opens it', async () => {
        render(oneLecture);

        button()!.click();
        await fixture.whenStable();

        expect(createSession).toHaveBeenCalledExactlyOnceWith({
            courseId: 1,
            context: { mode: ChatServiceMode.LECTURE, entityId: 10 },
            question: 'Why is merge sort O(n log n)?',
            answer: 'It halves the input.[cite:L:100:3:::Slides:]',
        });
        expect(router.navigate).toHaveBeenCalledExactlyOnceWith(['/courses', 1, 'iris'], { queryParams: { irisSession: 77 } });
    });

    it('opens the chat on the most cited lecture', async () => {
        render(twoLectures);

        button()!.click();
        await fixture.whenStable();

        expect(createSession).toHaveBeenCalledWith(expect.objectContaining({ courseId: 1, context: { mode: ChatServiceMode.LECTURE, entityId: 11 } }));
    });

    it('continues on Cmd+Enter and claims the key, but not on a plain Enter', () => {
        render(oneLecture);

        const plain = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
        window.dispatchEvent(plain);
        expect(createSession).not.toHaveBeenCalled();
        expect(plain.defaultPrevented).toBe(false);

        const modified = new KeyboardEvent('keydown', { key: 'Enter', metaKey: true, cancelable: true });
        window.dispatchEvent(modified);
        expect(createSession).toHaveBeenCalledOnce();
        expect(modified.defaultPrevented).toBe(true);
    });

    it('creates one chat however often the button is clicked while it is being created', () => {
        createSession.mockReturnValue(new Subject<IrisSession>());
        render(oneLecture);

        button()!.click();
        button()!.click();

        expect(createSession).toHaveBeenCalledOnce();
    });

    it('becomes usable again when the chat cannot be created', () => {
        createSession.mockReturnValue(throwError(() => new Error('403')));
        render(oneLecture);

        button()!.click();
        fixture.detectChanges();

        expect(button()!.disabled).toBe(false);
        expect(router.navigate).not.toHaveBeenCalled();
    });
});

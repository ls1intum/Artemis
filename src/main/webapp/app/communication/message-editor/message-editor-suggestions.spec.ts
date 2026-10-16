import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { MessageEditorSuggestions } from './message-editor-suggestions';
import { EmojiSearch } from '@ctrl/ngx-emoji-mart';
import { EMOTICON_TO_EMOJI } from 'app/editor/monaco-editor/model/emoticon-emoji.util';

describe('Message editor suggestions', () => {
    const emojis = { search: vi.fn() };
    const members = { searchMembersForUserMentions: vi.fn() };
    const channels = { getPublicChannelsOfCourse: vi.fn() };
    const exercises = { getTitlesForCourse: vi.fn() };
    const lectures = { findAllByCourseIdForOverview: vi.fn() };
    let suggestions: MessageEditorSuggestions;

    beforeEach(() => {
        vi.clearAllMocks();
        emojis.search.mockReturnValue([
            { id: 'joy', native: '😂' },
            { id: 'joy_cat', native: '😹' },
        ]);
        members.searchMembersForUserMentions.mockReturnValue(of(new HttpResponse({ body: [{ name: 'Anna', login: 'anna' }] })));
        channels.getPublicChannelsOfCourse.mockReturnValue(of(new HttpResponse({ body: [{ id: 2, name: 'general' }] })));
        exercises.getTitlesForCourse.mockReturnValue(of([{ id: 3, title: 'Exercise', type: 'programming' }]));
        lectures.findAllByCourseIdForOverview.mockReturnValue(of([{ id: 4, title: 'Lecture' }]));
        TestBed.configureTestingModule({
            providers: [
                MessageEditorSuggestions,
                { provide: EmojiSearch, useValue: emojis },
                { provide: CourseManagementService, useValue: members },
                { provide: ChannelService, useValue: channels },
                { provide: ExerciseService, useValue: exercises },
                { provide: LectureService, useValue: lectures },
            ],
        });
        suggestions = TestBed.inject(MessageEditorSuggestions);
    });

    it('completes emoji shortcodes as native glyphs without loading course references', async () => {
        await expect(suggestions.search(':', 'jo', 1)).resolves.toEqual([
            { label: '😂 :joy:', kind: 'emoji', markdown: '😂' },
            { label: '😹 :joy_cat:', kind: 'emoji', markdown: '😹' },
        ]);
        expect(emojis.search).toHaveBeenCalledExactlyOnceWith('jo', undefined, 30);
        expect(members.searchMembersForUserMentions).not.toHaveBeenCalled();
        expect(channels.getPublicChannelsOfCourse).not.toHaveBeenCalled();
        expect(exercises.getTitlesForCourse).not.toHaveBeenCalled();
        expect(lectures.findAllByCourseIdForOverview).not.toHaveBeenCalled();
    });

    it('keeps emoji relevance order and skips missing native glyphs before limiting results', async () => {
        const valid = Array.from({ length: 8 }, (_, index) => ({ id: `emoji${index}`, native: `😀${index}` }));
        emojis.search.mockReturnValue([{ id: 'custom' }, ...valid.slice(0, 3), { id: 'empty', native: '' }, ...valid.slice(3)]);
        const result = await suggestions.search(':', 'emoji', 1);
        expect(result).toHaveLength(5);
        expect(result.map((item) => item.markdown)).toEqual(valid.slice(0, 5).map((emoji) => emoji.native));
        expect(result.map((item) => item.label)).toEqual(valid.slice(0, 5).map((emoji) => `${emoji.native} :${emoji.id}:`));
    });

    it.each(['', ' joy', 'jo y', 'jo!', 'jo.', 'jo:', '(jo', '/path'])('does not search for invalid or empty shortcode term "%s"', async (query) => {
        await expect(suggestions.search(':', query, 1)).resolves.toEqual([]);
        expect(emojis.search).not.toHaveBeenCalled();
        expect(channels.getPublicChannelsOfCourse).not.toHaveBeenCalled();
    });

    it.each(Object.keys(EMOTICON_TO_EMOJI))('does not compete with the existing emoticon %s', async (emoticon) => {
        await expect(suggestions.search(':', emoticon.slice(1), 1)).resolves.toEqual([]);
        expect(emojis.search).not.toHaveBeenCalled();
    });

    it.each(['+1', '-1', 'rolling_on_the_floor_laughing', 'party'])('searches aliases and compound emoji shortcodes "%s"', async (query) => {
        await suggestions.search(':', query, 1);
        expect(emojis.search).toHaveBeenCalledExactlyOnceWith(query, undefined, 30);
    });

    it.each([undefined, null, []])('handles an empty emoji search result (%s)', async (result) => {
        emojis.search.mockReturnValue(result);
        await expect(suggestions.search(':', 'unmatched', 1)).resolves.toEqual([]);
    });

    it('combines channel, exercise, and lecture references without changing their wire format', async () => {
        const items = await suggestions.search('#', '', 1);
        expect(items.map((item) => item.markdown)).toEqual([
            '[channel]general(2)[/channel]',
            '[programming]Exercise(/courses/1/exercises/3)[/programming]',
            '[lecture]Lecture(/courses/1/lectures/4)[/lecture]',
        ]);
        await suggestions.search('#', 'lecture', 1);
        expect(channels.getPublicChannelsOfCourse).toHaveBeenCalledTimes(1);
        expect(lectures.findAllByCourseIdForOverview).toHaveBeenCalledTimes(1);
    });

    it('scopes caches to each course and filters results', async () => {
        expect((await suggestions.search('#', 'lect', 1)).map((item) => item.kind)).toEqual(['lecture']);
        await suggestions.search('#', '', 2);
        expect(channels.getPublicChannelsOfCourse).toHaveBeenCalledTimes(2);
    });

    it('retains numeric message references as explicit suggestions', async () => {
        expect((await suggestions.search('#', '123', 1))[0]).toMatchObject({ markdown: '#123', kind: 'message' });
    });

    it('only suggests all members when creating a group-chat message', async () => {
        const group = { id: 1, type: ConversationType.GROUP_CHAT };
        expect((await suggestions.search('@', '', 1, group))[0].markdown).toBe('@all');
        expect((await suggestions.search('@', '', 1, group, true)).some((item) => item.markdown === '@all')).toBe(false);
        expect((await suggestions.search('@', '', 1)).some((item) => item.markdown === '@all')).toBe(false);
    });

    it('retries failed content requests rather than caching the failure', async () => {
        channels.getPublicChannelsOfCourse.mockReturnValueOnce(throwError(() => new Error('offline')));
        await expect(suggestions.search('#', '', 1)).rejects.toThrow('offline');
        expect((await suggestions.search('#', '', 1)).length).toBe(3);
    });
});

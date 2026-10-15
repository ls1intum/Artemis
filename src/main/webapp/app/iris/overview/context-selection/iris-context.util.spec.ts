import { describe, expect, it } from 'vitest';
import { faChalkboardUser, faFont, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';
import { IrisJsonMessageContent, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { contextFromSwitchMarker, iconForEntityMode, parseContextSwitchMarker, routeForContext } from './iris-context.util';

describe('iconForEntityMode', () => {
    it('returns faChalkboardUser for LECTURE', () => {
        expect(iconForEntityMode(ChatServiceMode.LECTURE)).toBe(faChalkboardUser);
    });

    it('returns faKeyboard for PROGRAMMING_EXERCISE', () => {
        expect(iconForEntityMode(ChatServiceMode.PROGRAMMING_EXERCISE)).toBe(faKeyboard);
    });

    it('returns faFont for TEXT_EXERCISE', () => {
        expect(iconForEntityMode(ChatServiceMode.TEXT_EXERCISE)).toBe(faFont);
    });

    it('returns undefined for COURSE mode', () => {
        expect(iconForEntityMode(ChatServiceMode.COURSE)).toBeUndefined();
    });

    it('returns undefined for undefined input', () => {
        expect(iconForEntityMode(undefined)).toBeUndefined();
    });
});

describe('routeForContext', () => {
    const courseId = 10;
    const entityId = 99;

    it('returns exercise route for PROGRAMMING_EXERCISE', () => {
        expect(routeForContext(courseId, ChatServiceMode.PROGRAMMING_EXERCISE, entityId)).toBe(`/courses/${courseId}/exercises/${entityId}`);
    });

    it('returns exercise route for TEXT_EXERCISE', () => {
        expect(routeForContext(courseId, ChatServiceMode.TEXT_EXERCISE, entityId)).toBe(`/courses/${courseId}/exercises/${entityId}`);
    });

    it('returns lecture route for LECTURE', () => {
        expect(routeForContext(courseId, ChatServiceMode.LECTURE, entityId)).toBe(`/courses/${courseId}/lectures/${entityId}`);
    });

    it('returns undefined for COURSE mode', () => {
        expect(routeForContext(courseId, ChatServiceMode.COURSE, entityId)).toBeUndefined();
    });

    it('returns undefined when courseId is undefined', () => {
        expect(routeForContext(undefined, ChatServiceMode.PROGRAMMING_EXERCISE, entityId)).toBeUndefined();
    });

    it('returns undefined when mode is undefined', () => {
        expect(routeForContext(courseId, undefined, entityId)).toBeUndefined();
    });

    it('returns undefined when entityId is undefined', () => {
        expect(routeForContext(courseId, ChatServiceMode.PROGRAMMING_EXERCISE, undefined)).toBeUndefined();
    });
});

describe('parseContextSwitchMarker', () => {
    it('reads the marker from the first JSON content block', () => {
        const contents = [new IrisTextMessageContent('hello'), new IrisJsonMessageContent({ transition: 'changed', entityMode: ChatServiceMode.LECTURE, entityId: 5, name: 'L1' })];
        expect(parseContextSwitchMarker(contents)).toEqual({ transition: 'changed', entityMode: ChatServiceMode.LECTURE, entityId: 5, name: 'L1' });
    });

    it('returns an empty marker when no JSON content is present', () => {
        expect(parseContextSwitchMarker([new IrisTextMessageContent('hello')])).toEqual({});
    });
});

describe('contextFromSwitchMarker', () => {
    it('builds an entity context for added and changed transitions', () => {
        const marker = { transition: 'added' as const, entityMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 11, name: 'Sorting' };
        expect(contextFromSwitchMarker(marker, 7)).toEqual({ mode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 11, entityName: 'Sorting' });
    });

    it('builds the course context from the course id for a removed transition', () => {
        expect(contextFromSwitchMarker({ transition: 'removed' }, 7)).toEqual({ mode: ChatServiceMode.COURSE, entityId: 7 });
    });

    it('returns undefined for a removed transition without a course id', () => {
        expect(contextFromSwitchMarker({ transition: 'removed' }, undefined)).toBeUndefined();
    });

    it('returns undefined when the marker carries no entity', () => {
        expect(contextFromSwitchMarker({ transition: 'changed' }, 7)).toBeUndefined();
    });
});

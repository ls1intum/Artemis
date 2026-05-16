import { describe, expect, it } from 'vitest';
import { faChalkboardUser, faFont, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { iconForEntityMode, routeForContext } from './iris-context.util';

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

    it('returns undefined for unknown string', () => {
        expect(iconForEntityMode('UNKNOWN_MODE')).toBeUndefined();
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

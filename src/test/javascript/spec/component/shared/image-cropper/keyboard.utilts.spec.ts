import { getEventForKey, getInvertedPositionForKey, getPositionForKey } from 'app/shared/image-cropper/utils/keyboard.utils';

describe('Keyboard Utils', () => {
    describe('getPositionForKey', () => {
        it('should return correct position for ArrowUp', () => {
            expect(getPositionForKey('ArrowUp')).toBe('top');
        });

        it('should return correct position for ArrowRight', () => {
            expect(getPositionForKey('ArrowRight')).toBe('right');
        });

        it('should return correct position for ArrowDown', () => {
            expect(getPositionForKey('ArrowDown')).toBe('bottom');
        });

        it('should return correct position for ArrowLeft', () => {
            expect(getPositionForKey('ArrowLeft')).toBe('left');
        });

        it('should return default position for unknown key', () => {
            expect(getPositionForKey('UnknownKey')).toBe('left');
        });
    });

    describe('getInvertedPositionForKey', () => {
        it('should return correct inverted position for ArrowUp', () => {
            expect(getInvertedPositionForKey('ArrowUp')).toBe('bottom');
        });

        it('should return correct inverted position for ArrowRight', () => {
            expect(getInvertedPositionForKey('ArrowRight')).toBe('left');
        });

        it('should return correct inverted position for ArrowDown', () => {
            expect(getInvertedPositionForKey('ArrowDown')).toBe('top');
        });

        it('should return correct inverted position for ArrowLeft', () => {
            expect(getInvertedPositionForKey('ArrowLeft')).toBe('right');
        });

        it('should return default inverted position for unknown key', () => {
            expect(getInvertedPositionForKey('UnknownKey')).toBe('right');
        });
    });

    describe('getEventForKey', () => {
        const stepSize = 10;

        it('should return correct event for ArrowUp', () => {
            expect(getEventForKey('ArrowUp', stepSize)).toEqual({ clientX: 0, clientY: -stepSize });
        });

        it('should return correct event for ArrowRight', () => {
            expect(getEventForKey('ArrowRight', stepSize)).toEqual({ clientX: stepSize, clientY: 0 });
        });

        it('should return correct event for ArrowDown', () => {
            expect(getEventForKey('ArrowDown', stepSize)).toEqual({ clientX: 0, clientY: stepSize });
        });

        it('should return correct event for ArrowLeft', () => {
            expect(getEventForKey('ArrowLeft', stepSize)).toEqual({ clientX: -stepSize, clientY: 0 });
        });

        it('should return default event for unknown key', () => {
            expect(getEventForKey('UnknownKey', stepSize)).toEqual({ clientX: -stepSize, clientY: 0 });
        });
    });
});

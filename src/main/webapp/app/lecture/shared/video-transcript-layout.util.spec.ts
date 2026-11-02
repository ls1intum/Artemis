import { describe, expect, it, vi } from 'vitest';
import { resizeVideoColumn, syncVideoTranscriptHeight } from './video-transcript-layout.util';

describe('video transcript layout', () => {
    it('uses a clamped percentage flex basis and removes obsolete fixed widths', () => {
        const wrapper = document.createElement('div');
        const column = document.createElement('div');
        vi.spyOn(wrapper, 'getBoundingClientRect').mockReturnValue({ width: 400 } as DOMRect);
        column.style.width = '200px';
        resizeVideoColumn(column, wrapper, 100);
        expect(column.style.flex).toBe('0 0 25%');
        expect(column.style.width).toBe('');
        resizeVideoColumn(column, wrapper, 800);
        expect(column.style.flex).toBe('0 0 100%');
        resizeVideoColumn(column, wrapper, -10);
        expect(column.style.flex).toBe('0 0 0%');
    });
    it('ignores a hidden wrapper and sizes the transcript to at least its minimum height', () => {
        const wrapper = document.createElement('div');
        const column = document.createElement('div');
        const transcript = document.createElement('div');
        transcript.className = 'transcript-column';
        wrapper.appendChild(transcript);
        resizeVideoColumn(column, wrapper, 100);
        expect(column.style.flex).toBe('');
        syncVideoTranscriptHeight(column, wrapper, 300);
        expect(transcript.style.maxHeight).toBe('300px');
        Object.defineProperty(column, 'offsetHeight', { value: 500 });
        syncVideoTranscriptHeight(column, wrapper, 300);
        expect(transcript.style.maxHeight).toBe('500px');
        expect(() => resizeVideoColumn(undefined, undefined, 100)).not.toThrow();
        expect(() => syncVideoTranscriptHeight(undefined, undefined, 300)).not.toThrow();
    });
});

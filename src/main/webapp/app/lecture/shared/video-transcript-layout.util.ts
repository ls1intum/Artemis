/** Keep the split responsive after a user drags the video/transcript divider. */
export function resizeVideoColumn(videoColumn: HTMLElement | undefined, wrapper: HTMLElement | undefined, width: number): void {
    if (!videoColumn || !wrapper) {
        return;
    }
    const wrapperWidth = wrapper.getBoundingClientRect().width;
    if (wrapperWidth <= 0) {
        return;
    }
    const percent = Math.min(100, Math.max(0, (width / wrapperWidth) * 100));
    videoColumn.style.flex = `0 0 ${percent}%`;
    videoColumn.style.width = '';
}

/** Limit transcript scrolling to the video height, while retaining a readable minimum height. */
export function syncVideoTranscriptHeight(videoColumn: HTMLElement | undefined, wrapper: HTMLElement | undefined, minimumHeight: number): void {
    if (!videoColumn || !wrapper) {
        return;
    }
    const transcript = wrapper.querySelector<HTMLElement>('.transcript-column');
    if (transcript) {
        transcript.style.maxHeight = `${Math.max(videoColumn.offsetHeight, minimumHeight)}px`;
    }
}

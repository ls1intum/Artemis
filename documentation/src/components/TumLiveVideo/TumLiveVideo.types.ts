import React from 'react';

export type TumLiveVideoProps = {
    /**
     * The video ID or full URL for the TUM-Live video.
     * Can be either:
     * - Full URL: "https://tum.live/w/artemisintro/40748/PRES"
     * - Video ID: "40748" (will be prefixed with the base URL)
     */
    src: string;

    /**
     * The title of the video (used for accessibility and as iframe title)
     */
    title: string;

    /**
     * Width of the video iframe in pixels
     * @default 600
     */
    width?: number;

    /**
     * Height of the video iframe in pixels
     * @default 350
     */
    height?: number;

    /**
     * Whether to hide video controls
     * @default true
     */
    videoOnly?: boolean;

    /**
     * Start time in seconds (optional)
     */
    startTime?: number;

    /**
     * Additional inline styles for the iframe
     */
    style?: React.CSSProperties;
};
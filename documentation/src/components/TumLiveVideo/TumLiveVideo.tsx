import React from 'react';
import { TumLiveVideoProps } from './TumLiveVideo.types';

/**
 * A reusable component for embedding TUM-Live videos.
 *
 * @example
 * ```tsx
 * // Using full URL
 * <TumLiveVideo
 *   src="https://tum.live/w/artemisintro/40748/PRES"
 *   title="Video tutorial for configuring test case grading"
 * />
 *
 * // Using video ID (shorter form)
 * <TumLiveVideo
 *   src="40748"
 *   title="Video tutorial for configuring test case grading"
 * />
 *
 * // With custom dimensions and start time
 * <TumLiveVideo
 *   src="40748"
 *   title="Video tutorial"
 *   width={800}
 *   height={450}
 *   startTime={30}
 * />
 * ```
 */
const TumLiveVideo: React.FC<TumLiveVideoProps> = ({
    src,
    title,
    width = 600,
    height = 350,
    videoOnly = true,
    startTime,
    style,
}) => {
    // Build the full URL if only video ID is provided
    const buildVideoUrl = (): string => {
        let videoUrl: string;

        // Check if src is a full URL or just a video ID
        if (src.startsWith('http')) {
            videoUrl = src;
        } else {
            // Assume it's a video ID and build the full URL
            videoUrl = `https://tum.live/w/artemisintro/${src}/PRES`;
        }

        // Build query parameters
        const params = new URLSearchParams();
        if (videoOnly) {
            params.append('video_only', '1');
        }
        if (startTime !== undefined) {
            params.append('t', startTime.toString());
        }

        // Add query parameters to URL if any exist
        const queryString = params.toString();
        if (queryString) {
            return `${videoUrl}?${queryString}`;
        }

        return videoUrl;
    };

    const defaultStyle: React.CSSProperties = {
        border: 0,
        ...style,
    };

    return (
        <iframe
            src={buildVideoUrl()}
            title={title}
            width={width}
            height={height}
            allowFullScreen
            loading="lazy"
            allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
            referrerPolicy="no-referrer-when-downgrade"
            style={defaultStyle}
        >
            {title}
        </iframe>
    );
};

export default TumLiveVideo;

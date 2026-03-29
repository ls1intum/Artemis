// Shared types for PDF viewer iframe communication.

export type IframeMessageType = 'ready' | 'pageChange' | 'pagesLoaded' | 'loadPDF' | 'themeChange' | 'pdfLoadError' | 'download' | 'openFullscreen';

export interface IframeMessageData {
    page?: number;
    pagesCount?: number;
    url?: string;
    initialPage?: number;
    isDarkMode?: boolean;
    viewerMode?: 'embedded' | 'fullscreen';
}

export interface IframeMessage {
    type: IframeMessageType;
    data?: IframeMessageData;
}

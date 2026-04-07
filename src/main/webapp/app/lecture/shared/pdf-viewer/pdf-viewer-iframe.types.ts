// Shared types for PDF viewer iframe communication.

export type IframeMessageType =
    | 'ready'
    | 'pageChange'
    | 'pageRendered'
    | 'loadPDF'
    | 'themeChange'
    | 'languageChange'
    | 'viewerModeChange'
    | 'pdfLoadError'
    | 'download'
    | 'openFullscreen'
    | 'closeFullscreen';

export interface IframeMessageData {
    page?: number;
    url?: string;
    initialPage?: number;
    isDarkMode?: boolean;
    languageKey?: string;
    viewerMode?: 'embedded' | 'fullscreen';
}

export interface IframeMessage {
    type: IframeMessageType;
    data?: IframeMessageData;
}

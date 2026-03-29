// Shared types for PDF viewer iframe communication.

export type IframeMessageType = 'ready' | 'pageChange' | 'pagesLoaded' | 'loadPDF' | 'themeChange' | 'pdfLoadError' | 'download';

export interface IframeMessageData {
    page?: number;
    pagesCount?: number;
    url?: string;
    initialPage?: number;
    isDarkMode?: boolean;
}

export interface IframeMessage {
    type: IframeMessageType;
    data?: IframeMessageData;
}

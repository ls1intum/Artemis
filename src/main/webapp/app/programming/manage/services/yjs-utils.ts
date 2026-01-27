export const encodeUint8ArrayToBase64 = (data: Uint8Array): string => {
    let binary = '';
    const chunkSize = 0x8000;
    for (let i = 0; i < data.length; i += chunkSize) {
        binary += String.fromCharCode(...data.subarray(i, i + chunkSize));
    }
    return window.btoa(binary);
};

export const decodeBase64ToUint8Array = (base64: string): Uint8Array => {
    const binary = window.atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
};

export type AwarenessUpdatePayload = {
    added: number[];
    updated: number[];
    removed: number[];
};

export type YjsOrigin = 'remote' | 'init' | 'local' | 'timeout' | 'unknown';

export const normalizeYjsOrigin = (origin: unknown): YjsOrigin => {
    if (origin === 'remote' || origin === 'init' || origin === 'local' || origin === 'timeout') {
        return origin;
    }
    return 'unknown';
};

const remoteClientIds = new Set<number>();
const remoteClientColors = new Map<number, string>();
const REMOTE_STYLE_ELEMENT_ID = 'yjs-remote-selection-styles';

export const getColorForClientId = (clientId: number): string => {
    const cached = remoteClientColors.get(clientId);
    if (cached) {
        return cached;
    }
    const base = clientId.toString();
    let hash = 0;
    for (let i = 0; i < base.length; i++) {
        hash = (hash * 31 + base.charCodeAt(i)) | 0;
    }
    const normalized = Math.abs(hash) % 360;
    const color = `hsl(${normalized}, 70%, 45%)`;
    remoteClientColors.set(clientId, color);
    return color;
};

export const ensureRemoteSelectionStyle = (clientId: number, color: string) => {
    if (remoteClientIds.has(clientId)) {
        return;
    }
    if (typeof document === 'undefined') {
        return;
    }
    let styleElement = document.getElementById(REMOTE_STYLE_ELEMENT_ID) as HTMLStyleElement | null;
    if (!styleElement) {
        styleElement = document.createElement('style');
        styleElement.id = REMOTE_STYLE_ELEMENT_ID;
        document.head.appendChild(styleElement);
    }
    const safeColor = color || getColorForClientId(clientId);
    styleElement.appendChild(
        document.createTextNode(
            `.yRemoteSelection-${clientId}{background-color:${safeColor}33;}` +
                `.yRemoteSelectionHead-${clientId}{border-left:2px solid ${safeColor};}` +
                `.yRemoteSelectionHead-${clientId}::after{border-color:${safeColor};}`,
        ),
    );
    remoteClientIds.add(clientId);
};

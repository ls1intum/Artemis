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

type RemoteClientStyle = {
    color: string;
    name?: string;
};

const remoteClientStyles = new Map<number, RemoteClientStyle>();
const REMOTE_STYLE_ELEMENT_ID = 'yjs-remote-selection-styles';

export const clearRemoteSelectionStyles = () => {
    remoteClientStyles.clear();
    if (typeof document === 'undefined') {
        return;
    }
    document.getElementById(REMOTE_STYLE_ELEMENT_ID)?.remove();
};

export const getColorForClientId = (clientId: number): string => {
    const cached = remoteClientStyles.get(clientId)?.color;
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
    remoteClientStyles.set(clientId, { color });
    return color;
};

const escapeCssContent = (value: string): string => {
    return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
};

const getInitials = (value: string): string => {
    const trimmed = value.trim();
    if (!trimmed) {
        return '';
    }
    const parts = trimmed.split(/\s+/).filter(Boolean);
    if (parts.length === 1) {
        return parts[0].slice(0, 2).toUpperCase();
    }
    return (parts[0][0] + parts[1][0]).toUpperCase();
};

const renderRemoteSelectionStyles = () => {
    if (typeof document === 'undefined') {
        return;
    }
    let styleElement = document.getElementById(REMOTE_STYLE_ELEMENT_ID) as HTMLStyleElement | null;
    if (!styleElement) {
        styleElement = document.createElement('style');
        styleElement.id = REMOTE_STYLE_ELEMENT_ID;
        document.head.appendChild(styleElement);
    }
    const rules: string[] = [];
    remoteClientStyles.forEach((style, clientId) => {
        const safeColor = style.color || getColorForClientId(clientId);
        const label = style.name ? escapeCssContent(style.name) : '';
        const initials = style.name ? escapeCssContent(getInitials(style.name)) : '';
        rules.push(
            `
.monaco-editor .yRemoteSelection-${clientId},.yRemoteSelection-${clientId} {
    background-color:${safeColor}55 !important;
    border-radius:2px;
    outline:1px solid ${safeColor}aa !important;
    box-shadow:inset 0 0 0 9999px ${safeColor}22;
}
.yRemoteSelectionHead-${clientId} {
    border-right:2px solid ${safeColor};
    position:relative;
}
.yRemoteSelectionHead-${clientId}::after {
    border-color:${safeColor};
}
.yRemoteSelectionHead-${clientId}::after {
    content:"${initials || label}";
    background:${safeColor};
    color:#fff;
    font-size:10px;
    line-height:1;
    border-radius:3px;
    padding:2px 4px;
    white-space:nowrap;
    position:absolute;
    left:2px;top:-18px;
    opacity:0.7;
    z-index:5;
    user-select:none;
    box-shadow:0 1px 2px rgba(0,0,0,0.25);
}
.yRemoteSelectionHead-${clientId}:hover::after {
    content:"${label}";
    opacity:0.9;
}`,
        );
    });
    styleElement.textContent = rules.join('');
};

export const ensureRemoteSelectionStyle = (clientId: number, color: string, displayName?: string) => {
    const safeColor = color || getColorForClientId(clientId);
    const existing = remoteClientStyles.get(clientId);
    if (existing && existing.color === safeColor && existing.name === displayName) {
        return;
    }
    remoteClientStyles.set(clientId, { color: safeColor, name: displayName });
    renderRemoteSelectionStyles();
};

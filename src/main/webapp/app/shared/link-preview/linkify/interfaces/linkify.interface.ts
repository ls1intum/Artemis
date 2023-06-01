export interface Link {
    type: string;
    value: string;
    href: string;
}

export interface LinkifyConfig {
    enableHash?: boolean;
    enableMention?: boolean;
}

export interface LinkifyOptions {
    attributes?: any;
    className?: string;
    defaultProtocol?: string;
    events?: any;
    ignoreTags?: Array<any>;
    nl2br?: boolean;
    tagName?: string;
    target?: { url: string };
    validate?: boolean;

    format?(value: any, type: any): any;

    formatHref?(href: any, type: any): any;
}

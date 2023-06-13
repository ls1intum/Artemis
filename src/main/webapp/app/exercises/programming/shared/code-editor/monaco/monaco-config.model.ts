// export type MonacoTheme = 'vs' | 'vs-dark' | 'hc-black' | 'hc-light';
export enum MonacoTheme {
    VS = 'vs',
    VS_DARK = 'vs-dark',
    HC_BLACK = 'hc-black',
    HC_LIGHT = 'hc-light',
}

export class MonacoConfigModel {
    public fontSize: number;
    public theme: MonacoTheme;
    public minFontSize: number;
    public maxFontSize: number;
    public minimap: boolean;

    constructor(fontSize?: number, theme?: MonacoTheme, minFontSize?: number, maxFontSize?: number, minimap?: boolean) {
        this.fontSize = fontSize || 15;
        this.theme = theme || MonacoTheme.VS;
        this.minFontSize = minFontSize || 5;
        this.maxFontSize = maxFontSize || 40;
        this.minimap = minimap || true;
    }
}

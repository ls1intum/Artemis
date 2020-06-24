export interface Fingerprint2 {
    get(callback: (components: any) => void): void;
    get(opions: any, callback: (components: any) => void): void;
    x64hash128(key: string, seed: number): string;
}

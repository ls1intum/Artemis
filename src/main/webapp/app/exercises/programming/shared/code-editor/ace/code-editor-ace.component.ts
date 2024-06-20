export type Annotation = { fileName: string; row: number; column: number; text: string; type: string; timestamp: number; hash?: string };
export type FileSession = { [fileName: string]: { code: string; cursor: { column: number; row: number }; loadingError: boolean } };

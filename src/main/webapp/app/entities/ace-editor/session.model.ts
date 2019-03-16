import { AceAnnotation } from './annotation.model';

export type Session = { ts: number; errors: { [fileName: string]: AceAnnotation[] } };

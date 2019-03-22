import { AceAnnotation } from './annotation.model';

export type Session = { timestamp: number; errors: { [fileName: string]: AceAnnotation[] } };

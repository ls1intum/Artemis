import { Language } from 'app/entities/course.model';
import { of } from 'rxjs';

export class MockTextEditorService {
    get = (participationId: number) => of();
    predictLanguage = (text: string): Language => Language.ENGLISH;
}

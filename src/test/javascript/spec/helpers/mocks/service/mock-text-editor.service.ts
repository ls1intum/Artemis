import { of } from 'rxjs';
import { Language } from 'app/entities/course.model';

export class MockTextEditorService {
    get = (participationId: number) => of();
    predictLanguage = (text: string): Language => Language.ENGLISH;
}

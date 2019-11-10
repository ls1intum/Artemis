import { of } from 'rxjs';

export class MockTextEditorService {
    get = (id: number) => of();
}

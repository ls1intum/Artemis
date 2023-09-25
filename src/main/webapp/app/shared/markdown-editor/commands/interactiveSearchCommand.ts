import { Observable } from 'rxjs';
import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { HttpResponse } from '@angular/common/http';

export type KeyBinds = { win: string; mac: string };

export abstract class InteractiveSearchCommand extends MultiOptionCommand {
    execute(): void {}

    abstract performSearch(searchTerm: string): Observable<HttpResponse<any[]>>;

    abstract selectionToText(selected: any): void;
}

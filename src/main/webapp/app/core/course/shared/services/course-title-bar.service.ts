// src/app/shared/title-bar.service.ts
import { Injectable, Signal, TemplateRef, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CourseTitleBarService {
    private _titleTpl = signal<TemplateRef<any> | undefined>(undefined);
    private _actionsTpl = signal<TemplateRef<any> | undefined>(undefined);

    /** public read-only signals */
    readonly titleTpl: Signal<TemplateRef<any> | undefined> = this._titleTpl;
    readonly actionsTpl: Signal<TemplateRef<any> | undefined> = this._actionsTpl;

    /** called by our directives on init/destroy */
    setTitleTpl(tpl: TemplateRef<any> | undefined) {
        this._titleTpl.set(tpl);
    }
    setActionsTpl(tpl: TemplateRef<any> | undefined) {
        this._actionsTpl.set(tpl);
    }
}

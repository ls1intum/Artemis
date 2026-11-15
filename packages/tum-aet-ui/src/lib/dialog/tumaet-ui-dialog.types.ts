import { InputSignalWithTransform, Type } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { TumAetUiDialogSize } from './tumaet-ui-dialog.component';

/** Values writable to the signal inputs of one dialog content component. */
export type TumAetUiDialogInputs<T> = {
    [K in keyof T as T[K] extends InputSignalWithTransform<infer _Read, infer _Write> ? K : never]?: T[K] extends InputSignalWithTransform<infer _Read, infer Write>
        ? Write
        : never;
};

/** Options for a programmatically created, modal TUM AET UI dialog. */
export interface TumAetUiDialogOptions<D = Record<string, unknown>, T = unknown> {
    header?: string;
    ariaLabel?: string;
    size?: TumAetUiDialogSize;
    showHeader?: boolean;
    closable?: boolean;
    closeOnEscape?: boolean;
    dismissableMask?: boolean;
    autoFocus?: 'dialog' | 'first-tabbable';
    inputValues?: TumAetUiDialogInputs<T>;
    data?: D;
    templates?: { header?: Type<unknown>; footer?: Type<unknown> };
}

/** Per-dialog data available to dynamically created content, header and footer components. */
export class TumAetUiDialogConfig<D = Record<string, unknown>, T = unknown> {
    constructor(readonly options: TumAetUiDialogOptions<D, T>) {}

    get data(): D | undefined {
        return this.options.data;
    }

    get header(): string | undefined {
        return this.options.header;
    }
}

/** Handle shared by the opener and the content of one programmatic dialog. */
export class TumAetUiDialogRef<R = unknown, T = unknown> {
    private readonly closeEvents = new Subject<R | undefined>();
    private readonly dismissEvents = new Subject<unknown>();
    readonly onClose: Observable<R | undefined> = this.closeEvents.asObservable();
    readonly closed = this.onClose;
    readonly dismissed = this.dismissEvents.asObservable();
    readonly result: Promise<R | undefined>;
    componentInstance!: T;

    private resolveResult!: (value: R | undefined) => void;
    private rejectResult!: (reason?: unknown) => void;
    private settled = false;

    constructor(
        private readonly dispose: () => void,
        private readonly updateInputs: (values: TumAetUiDialogInputs<T>) => void,
    ) {
        this.result = new Promise<R | undefined>((resolve, reject) => {
            this.resolveResult = resolve;
            this.rejectResult = reject;
        });
        // Observable-only consumers need not attach a rejection handler for cancellation.
        void this.result.catch(() => {});
    }

    /** Set an Angular input, including signal inputs, on the dialog's content component. */
    setInputs(values: TumAetUiDialogInputs<T>): void {
        if (!this.settled) {
            this.updateInputs(values);
        }
    }

    close(value?: R): void {
        this.finish(value, false);
    }

    dismiss(reason: unknown = 'dismissed'): void {
        this.finish(undefined, true, reason);
    }

    private finish(value: R | undefined, dismissed: boolean, reason?: unknown): void {
        if (this.settled) {
            return;
        }
        this.settled = true;
        this.dispose();
        if (dismissed) {
            this.rejectResult(reason);
            this.dismissEvents.next(reason);
        } else {
            this.resolveResult(value);
        }
        this.closeEvents.next(value);
        this.closeEvents.complete();
        this.dismissEvents.complete();
    }
}

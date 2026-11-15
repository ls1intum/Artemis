import { setInputs } from '../internal/set-inputs';
import { ApplicationRef, ComponentRef, EnvironmentInjector, Injector, OnDestroy, Service, Type, createComponent, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { TumAetUiDynamicDialogHostComponent } from './tumaet-ui-dynamic-dialog-host.component';
import { TumAetUiDialogConfig, TumAetUiDialogInputs, TumAetUiDialogOptions, TumAetUiDialogRef } from './tumaet-ui-dialog.types';

/** Opens component content with the same accessible modal shell as `tumaet-ui-dialog`. */
@Service()
export class TumAetUiDialogService implements OnDestroy {
    private readonly applicationRef = inject(ApplicationRef);
    private readonly environmentInjector = inject(EnvironmentInjector);
    private readonly injector = inject(Injector);
    private readonly document = inject(DOCUMENT);
    private readonly openDialogs = new Set<{ dismiss(): void }>();

    open<T, R = unknown, D = Record<string, unknown>>(component: Type<T>, options: TumAetUiDialogOptions<D, T> = {}): TumAetUiDialogRef<R, T> {
        const hasVisibleTitle = options.showHeader !== false && (options.header?.trim() || options.templates?.header);
        if (!hasVisibleTitle && !options.ariaLabel?.trim()) {
            throw new Error('A programmatic dialog requires an accessible name through header, a header template, or ariaLabel');
        }
        let content: ComponentRef<T> | undefined;
        let host: ComponentRef<TumAetUiDynamicDialogHostComponent> | undefined;
        const ref = new TumAetUiDialogRef<R, T>(
            () => {
                this.openDialogs.delete(ref);
                if (host) {
                    this.applicationRef.detachView(host.hostView);
                    host.destroy();
                    host.location.nativeElement.remove();
                }
                if (content) {
                    this.applicationRef.detachView(content.hostView);
                    content.destroy();
                }
                dialogInjector.destroy();
            },
            (values) => {
                if (content) this.applyInputs(content, values);
            },
        );
        const dialogInjector = Injector.create({
            parent: this.injector,
            providers: [
                { provide: TumAetUiDialogRef, useValue: ref },
                { provide: TumAetUiDialogConfig, useValue: new TumAetUiDialogConfig(options) },
            ],
        });
        try {
            content = createComponent(component, { environmentInjector: this.environmentInjector, elementInjector: dialogInjector });
            if (options.inputValues) {
                this.applyInputs(content, options.inputValues);
            }
            ref.componentInstance = content.instance;
            host = createComponent(TumAetUiDynamicDialogHostComponent, {
                environmentInjector: this.environmentInjector,
                elementInjector: dialogInjector,
                projectableNodes: [[content.location.nativeElement]],
            });
            this.document.body.appendChild(host.location.nativeElement);
            this.applicationRef.attachView(content.hostView);
            this.applicationRef.attachView(host.hostView);
            this.openDialogs.add(ref);
            host.changeDetectorRef.detectChanges();
            return ref;
        } catch (error) {
            ref.dismiss(error);
            throw error;
        }
    }

    private applyInputs<T>(ref: ComponentRef<T>, values: TumAetUiDialogInputs<T>): void {
        // The public options map restricts every key to a writable signal input. Preserve that
        // relationship when passing a generic component to the helper's stricter union check.
        setInputs<T, TumAetUiDialogInputs<T>>(ref, values as Parameters<typeof setInputs<T, TumAetUiDialogInputs<T>>>[1]);
    }

    hasOpenDialogs(): boolean {
        return this.openDialogs.size > 0;
    }

    closeAll(): void {
        for (const ref of [...this.openDialogs]) {
            ref.dismiss();
        }
    }

    ngOnDestroy(): void {
        this.closeAll();
    }
}

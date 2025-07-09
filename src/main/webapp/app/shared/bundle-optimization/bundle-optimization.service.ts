import { Injectable } from '@angular/core';

/**
 * Central service for coordinating bundle optimization and preloading.
 * This service dynamically loads large libraries (Monaco, Apollon, PDF.js, NGX Charts)
 * and their related components on demand to reduce the initial application bundle size.
 * It preloads all major libraries during browser idle time to make them available
 * faster when they are actually needed.
 */
@Injectable({
    providedIn: 'root',
})
export class BundleOptimizationService {
    private monacoLoadingPromise: Promise<typeof import('monaco-editor')> | null = null;
    private apollonLoadingPromise: Promise<typeof import('@ls1intum/apollon')> | null = null;
    private pdfLoadingPromise: Promise<typeof import('pdfjs-dist')> | null = null;
    private ngxChartsLoadingPromise: Promise<typeof import('@swimlane/ngx-charts')> | null = null;

    private monacoEditorComponentPromise: Promise<any> | null = null;
    private apollonEditorComponentPromise: Promise<any> | null = null;
    private pdfPreviewComponentPromise: Promise<any> | null = null;
    private chartComponentsCache: Map<string, Promise<any>> = new Map();

    private preloadingInProgress = false;

    constructor() {
        this.preloadAllOnIdle();
    }

    // Kicks off the preloading of all major libraries as soon as the browser is idle.
    private preloadAllOnIdle(): void {
        if ('requestIdleCallback' in window) {
            window.requestIdleCallback(async () => {
                if (this.preloadingInProgress) {
                    return;
                }
                this.preloadingInProgress = true;
                try {
                    await Promise.allSettled([this.loadMonaco(), this.loadApollon(), this.loadPdfJs(), this.loadNgxCharts()]);
                } finally {
                    this.preloadingInProgress = false;
                }
            });
        } else {
            // Fallback for browsers without requestIdleCallback
            setTimeout(async () => {
                if (this.preloadingInProgress) {
                    return;
                }
                this.preloadingInProgress = true;
                try {
                    await Promise.allSettled([this.loadMonaco(), this.loadApollon(), this.loadPdfJs(), this.loadNgxCharts()]);
                } finally {
                    this.preloadingInProgress = false;
                }
            }, 1000);
        }
    }

    // Monaco Loader

    async loadMonaco(): Promise<typeof import('monaco-editor')> {
        if (this.monacoLoadingPromise) {
            return this.monacoLoadingPromise;
        }
        this.monacoLoadingPromise = this.performMonacoLoad();
        return this.monacoLoadingPromise;
    }

    private async performMonacoLoad(): Promise<typeof import('monaco-editor')> {
        try {
            const monaco = await import('monaco-editor');
            if (!self.MonacoEnvironment) {
                self.MonacoEnvironment = {
                    getWorkerUrl: () => '/vs/editor/editor.worker.js',
                };
            }
            return monaco;
        } catch (error) {
            this.monacoLoadingPromise = null;
            throw error;
        }
    }

    async loadMonacoEditorComponent(): Promise<any> {
        if (this.monacoEditorComponentPromise) {
            return this.monacoEditorComponentPromise;
        }
        this.monacoEditorComponentPromise = (async () => {
            await this.loadMonaco();
            const module = await import('../monaco-editor/monaco-editor.component');
            return module.MonacoEditorComponent;
        })();
        return this.monacoEditorComponentPromise;
    }

    // Apollon Loader

    async loadApollon(): Promise<typeof import('@ls1intum/apollon')> {
        if (this.apollonLoadingPromise) {
            return this.apollonLoadingPromise;
        }
        this.apollonLoadingPromise = this.performApollonLoad();
        return this.apollonLoadingPromise;
    }

    private async performApollonLoad(): Promise<typeof import('@ls1intum/apollon')> {
        try {
            return await import('@ls1intum/apollon');
        } catch (error) {
            this.apollonLoadingPromise = null;
            throw error;
        }
    }

    async loadApollonEditorComponent(): Promise<any> {
        if (this.apollonEditorComponentPromise) {
            return this.apollonEditorComponentPromise;
        }
        this.apollonEditorComponentPromise = (async () => {
            await this.loadApollon();
            const module = await import('app/quiz/manage/apollon-diagrams/detail/apollon-diagram-detail.component');
            return module.ApollonDiagramDetailComponent;
        })();
        return this.apollonEditorComponentPromise;
    }

    // PDF Loader

    async loadPdfJs(): Promise<typeof import('pdfjs-dist')> {
        if (this.pdfLoadingPromise) {
            return this.pdfLoadingPromise;
        }
        this.pdfLoadingPromise = this.performPdfLoad();
        return this.pdfLoadingPromise;
    }

    private async performPdfLoad(): Promise<typeof import('pdfjs-dist')> {
        try {
            const pdfjs = await import('pdfjs-dist');
            pdfjs.GlobalWorkerOptions.workerSrc = '/pdf.worker.mjs';
            return pdfjs;
        } catch (error) {
            this.pdfLoadingPromise = null;
            throw error;
        }
    }

    async loadPdfPreviewComponent(): Promise<any> {
        if (this.pdfPreviewComponentPromise) {
            return this.pdfPreviewComponentPromise;
        }
        this.pdfPreviewComponentPromise = (async () => {
            await this.loadPdfJs();
            const module = await import('app/lecture/manage/pdf-preview/pdf-preview.component');
            return module.PdfPreviewComponent;
        })();
        return this.pdfPreviewComponentPromise;
    }

    // Chart Loader

    async loadNgxCharts(): Promise<typeof import('@swimlane/ngx-charts')> {
        if (this.ngxChartsLoadingPromise) {
            return this.ngxChartsLoadingPromise;
        }
        this.ngxChartsLoadingPromise = this.performNgxChartsLoad();
        return this.ngxChartsLoadingPromise;
    }

    private async performNgxChartsLoad(): Promise<typeof import('@swimlane/ngx-charts')> {
        try {
            return await import('@swimlane/ngx-charts');
        } catch (error) {
            this.ngxChartsLoadingPromise = null;
            throw error;
        }
    }

    async loadChartComponent(componentName: string): Promise<any> {
        if (this.chartComponentsCache.has(componentName)) {
            return this.chartComponentsCache.get(componentName)!;
        }
        const loadPromise = this.performChartComponentLoad(componentName);
        this.chartComponentsCache.set(componentName, loadPromise);
        return loadPromise;
    }

    private async performChartComponentLoad(componentName: string): Promise<any> {
        await this.loadNgxCharts();
        try {
            switch (componentName) {
                case 'DoughnutChartComponent':
                    return (await import('app/exercise/statistics/doughnut-chart/doughnut-chart.component')).DoughnutChartComponent;
                case 'TestCaseDistributionChartComponent':
                    return (await import('app/programming/manage/grading/charts/test-case-distribution-chart.component')).TestCaseDistributionChartComponent;
                default:
                    throw new Error(`Unknown chart component: ${componentName}`);
            }
        } catch (error) {
            this.chartComponentsCache.delete(componentName);
            throw error;
        }
    }

    //  Status Utility

    /**
     * Gets the current loading status of the major libraries.
     * Returns true if loading has been initiated, false otherwise.
     */
    getLoadingStatus(): { monaco: boolean; apollon: boolean; pdf: boolean; charts: boolean } {
        return {
            monaco: !!this.monacoLoadingPromise,
            apollon: !!this.apollonLoadingPromise,
            pdf: !!this.pdfLoadingPromise,
            charts: !!this.ngxChartsLoadingPromise,
        };
    }
}

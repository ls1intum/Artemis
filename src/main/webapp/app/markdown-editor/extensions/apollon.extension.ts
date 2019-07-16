import { ApplicationRef, EmbeddedViewRef, ComponentFactoryResolver, Injector } from '@angular/core';
import * as showdown from 'showdown';
import { ApollonDiagram, ApollonDiagramService } from 'app/entities/apollon-diagram';
import { ModelingEditorComponent } from 'app/modeling-editor';

export const ApollonExtension = (componentFactoryResolver: ComponentFactoryResolver, appRef: ApplicationRef, injector: Injector, apollonService: ApollonDiagramService) => {
    const extension: showdown.ShowdownExtension = {
        type: 'lang',
        filter: (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
            const regex = /(?!\[apollon\])(\d+?)(?=\[\/apollon\])/g;
            const match = regex.exec(text);
            if (match) {
                const diagramId: number = Number(JSON.parse(match[1]));
                apollonService
                    .find(diagramId)
                    .map(({ body }) => body)
                    .toPromise()
                    .then((diagram: ApollonDiagram) => {
                        const componentRef = componentFactoryResolver.resolveComponentFactory(ModelingEditorComponent).create(injector);
                        componentRef.instance.readOnly = true;
                        componentRef.instance.umlModel = JSON.parse(diagram.jsonRepresentation);
                        appRef.attachView(componentRef.hostView);
                        const domElem = (componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
                        const apollonContainer = document.getElementById(`apollon-${diagramId}`);
                        if (apollonContainer) {
                            apollonContainer.innerHTML = '';
                            apollonContainer.append(domElem);
                        }
                    });
                return `<div id="apollon-${diagramId}"></div>`;
            }
            return text;
        },
    };
    return extension;
};

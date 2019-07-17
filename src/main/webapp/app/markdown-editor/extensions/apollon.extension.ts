import { ApplicationRef, EmbeddedViewRef, ComponentFactoryResolver, Injector } from '@angular/core';
import * as showdown from 'showdown';
import { ApollonDiagram, ApollonDiagramService } from 'app/entities/apollon-diagram';
import { ModelingEditorComponent } from 'app/modeling-editor';

export const ApollonExtension = (componentFactoryResolver: ComponentFactoryResolver, appRef: ApplicationRef, injector: Injector, apollonService: ApollonDiagramService) => {
    const extension: showdown.ShowdownExtension = {
        type: 'lang',
        filter: (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
            const idPlaceholder = '%idPlaceholder%';
            const regex = /(?!\[apollon\])(\d+?)(?=\[\/apollon\])/g;
            const regexTemplate = `\\[apollon\\]${idPlaceholder}\\[\\/apollon\\]`;
            const apollonHtmlContainer = `<div id="apollon-${idPlaceholder}"></div>`;
            const apollonDiagrams: string[] = text.match(regex) || [];
            const replacedText = apollonDiagrams.reduce(
                (acc: string, id: string): string => acc.replace(new RegExp(regexTemplate.replace(idPlaceholder, id), 'g'), apollonHtmlContainer.replace(idPlaceholder, id)),
                text,
            );
            apollonDiagrams
                .reduce((acc: string[], x: string) => (acc.includes(x) ? acc : [...acc, x]), [])
                .forEach((diagramId: string) => {
                    apollonService
                        .find(Number(diagramId))
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
                });
            return replacedText;
        },
    };
    return extension;
};

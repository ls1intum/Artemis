export interface ComponentCanDeactivate {
    canDeactivate: () => boolean;
    canDeactivateWarning?: string;
}

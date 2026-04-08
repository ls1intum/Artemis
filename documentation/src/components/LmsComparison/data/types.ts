import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';

export enum SupportLevel {
    Supported = 'supported',
    Partial = 'partial',
    None = 'none',
}

export enum PlatformId {
    Artemis = 'artemis',
    Canvas = 'canvas',
    Moodle = 'moodle',
    Blackboard = 'blackboard',
    ILIAS = 'ilias',
    OpenOlat = 'openolat',
}

export interface PlatformInfo {
    id: PlatformId;
    name: string;
}

export interface Feature {
    id: string;
    name: string;
    tooltip?: string;
    support: Record<PlatformId, SupportLevel>;
    notes?: Partial<Record<PlatformId, string>>;
}

export interface FeatureCategory {
    id: string;
    name: string;
    icon: IconDefinition;
    features: Feature[];
}

export interface HighlightCardData {
    icon: IconDefinition;
    title: string;
    description: string;
    borderColor: string;
}

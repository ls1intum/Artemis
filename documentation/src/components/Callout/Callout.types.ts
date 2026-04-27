import React from "react";

export enum CalloutVariant {
    success = 'success',
    info = 'info',
    tip = 'tip',
    warning = 'warning',
    danger = 'danger',
}

export type CalloutProps = {
    children: React.ReactNode;
    variant?: CalloutVariant;
};

export const CALLOUT_STYLE_CONFIG: Record<CalloutVariant, {
    backgroundColor: string;
    borderColor: string;
    icon: string;
    label: string;
}> = {
    success: {
        backgroundColor: 'var(--ifm-color-success-contrast-background)',
        borderColor: 'var(--ifm-color-success-dark)',
        icon: '✅',
        label: 'Success',
    },
    info: {
        backgroundColor: 'var(--ifm-color-info-contrast-background)',
        borderColor: 'var(--ifm-color-info-dark)',
        icon: 'ℹ️',
        label: 'Information',
    },
    tip: {
        backgroundColor: 'var(--ifm-color-success-contrast-background)',
        borderColor: 'var(--ifm-color-success-dark)',
        icon: '💡',
        label: 'Tip',
    },
    warning: {
        backgroundColor: 'var(--ifm-color-warning-contrast-background)',
        borderColor: 'var(--ifm-color-warning-dark)',
        icon: '⚠️',
        label: 'Warning',
    },
    danger: {
        backgroundColor: 'var(--ifm-color-danger-contrast-background)',
        borderColor: 'var(--ifm-color-danger-dark)',
        icon: '❗',
        label: 'Danger',
    },
};

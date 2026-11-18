import React from 'react';
import {
    CALLOUT_STYLE_CONFIG,
    CalloutProps,
    CalloutVariant,
} from './Callout.types';

const Callout: React.FC<CalloutProps> = ({
                                             children,
                                             variant = CalloutVariant.success,
                                         }) => {
    const currentStyle = CALLOUT_STYLE_CONFIG[variant];

    const bannerStyle: React.CSSProperties = {
        backgroundColor: currentStyle.backgroundColor,
        borderLeft: `5px solid ${currentStyle.borderColor}`,
        padding: '15px',
        borderRadius: '5px',
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        margin: '0.5rem',
    };

    const iconStyle: React.CSSProperties = {
        fontSize: '1.2em',
        alignSelf: 'center',
        flexShrink: 0,
    };

    const contentStyle: React.CSSProperties = {
        flex: 1,
        minWidth: 0,
    };

    return (
        <aside style={bannerStyle} role="note" className="callout">
            <span style={iconStyle} aria-hidden="true">{currentStyle.icon}</span>
            <div style={contentStyle} className="callout-content">
                <span className="sr-only">{currentStyle.label}: </span>
                {children}
            </div>
        </aside>
    );
};

export default Callout;

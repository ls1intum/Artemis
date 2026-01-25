import React from 'react';

/**
 * A reusable, styled image component.
 * It includes default styling which can be extended or overridden via the `style` prop.
 *
 * @param {object} props - The component props.
 * @param {string} props.src - The source URL for the image (required).
 * @param {string} props.alt - The alternative text for the image (required).
 * @param {ImageSize} [props.size] - The size of the image (default: medium).
 * @param {object} [props.style] - Optional style object to merge with default styles.
 * @param {object} [props.style] - Optional style object to merge with default styles.
 * @param {string} [props.caption] - Optional caption that is displayed below the image..
 * @returns {React.ReactElement} The rendered image component.
 */
const Image = ({
                   src,
                   alt,
                   size = ImageSize.medium,
                   style,
                   hideBorder,
                   caption,
                   ...rest
               }: {
    src: string | React.ComponentType<React.SVGProps<SVGSVGElement>>;
    alt: string;
    size?: ImageSize;
    style?: React.CSSProperties;
    hideBorder?: boolean;
    caption?: string;
}): React.ReactElement => {
    const sizeStyles: Record<ImageSize, React.CSSProperties> = {
        [ImageSize.small]: { maxWidth: '300px' },
        [ImageSize.medium]: { maxWidth: '600px' },
        [ImageSize.large]: { maxWidth: '100%' },
    };

    const defaultImageStyles: React.CSSProperties = {
        ...sizeStyles[size],
        width: 'auto',
        height: 'auto',
        objectFit: 'contain',
        border: hideBorder ? 'none' : '1px solid var(--ifm-color-emphasis-300)',
        borderRadius: '8px',
        margin: '0',
        padding: '0.5rem', // Internal padding around the image
        display: 'block', // Prevents extra space below the image
        imageRendering: '-webkit-optimize-contrast',
        backfaceVisibility: 'hidden', // Prevents blurry rendering in some browsers
        transform: 'translateZ(0)', // Forces GPU acceleration for sharper rendering
    };

    // Custom styles will override default styles if there's a conflict.
    const combinedImageStyles: React.CSSProperties = { ...defaultImageStyles, ...style };

    const renderImage = () => {
        if (typeof src === 'string') {
            return <img src={src} alt={alt} style={combinedImageStyles} {...rest} />;
        }
        // SVG component imported via @svgr
        const SvgComponent = src as React.FC<React.SVGProps<SVGSVGElement>>;
        return <SvgComponent role="img" aria-label={alt} style={combinedImageStyles} />;
    };

    return (
        <figure style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.125rem', margin: '1.5rem 0', width: 'fit-content' }}>
            {renderImage()}
            {caption && <figcaption style={{ fontSize: '0.75rem', fontWeight: 'bold', textAlign: 'center' }}>{caption}</figcaption>}
        </figure>
    );
};

export enum ImageSize {
    small = 'small',
    medium = 'medium',
    large = 'large'
}

export default Image;

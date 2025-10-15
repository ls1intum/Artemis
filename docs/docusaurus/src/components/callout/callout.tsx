import React from "react";
import {
  CALLOUT_STYLE_CONFIG,
  CalloutProps,
  CalloutVariant,
} from "./callout.types";

const Callout: React.FC<CalloutProps> = ({
  children,
  variant = CalloutVariant.success,
}) => {
  const currentStyle = CALLOUT_STYLE_CONFIG[variant];

  const bannerStyle: React.CSSProperties = {
    backgroundColor: currentStyle.backgroundColor,
    borderLeft: `5px solid ${currentStyle.borderColor}`,
    padding: "15px",
    borderRadius: "5px",
    display: "flex",
    alignItems: "center",
    gap: "12px",
  };

  return (
    <div style={bannerStyle} role="alert">
      <span style={{ fontSize: "1.2em", alignSelf: "center" }}>
        {currentStyle.icon}
      </span>
      <span
        style={{
          display: "flex",
          alignItems: "center",
          height: "100%",
          flex: 1,
          margin: 0,
        }}
      >
        <div style={{ width: "100%" }}>
          {React.Children.map(children, (child) =>
            React.isValidElement(child) && child.type === "p"
              ? // @ts-ignore the style property is present on p elements in which the markdown appears to be wrapped
                React.cloneElement(child, { style: { margin: 0 } })
              : child,
          )}
        </div>
      </span>
    </div>
  );
};

export default Callout;

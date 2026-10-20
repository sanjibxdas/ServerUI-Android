import type { SVGProps } from "react";

export type ServerArtProps = SVGProps<SVGSVGElement> & { size: number };

function Mark({ size, children, ...rest }: ServerArtProps) {
  return (
    <svg viewBox="0 0 32 32" width={size} height={size} aria-hidden focusable="false" {...rest}>
      {children}
    </svg>
  );
}

/** Original filled marks. Colors stay on the artwork so light and dark both read. */
export function ServerArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <rect x="8" y="4" width="16" height="24" rx="3" fill="#3A3A3C" />
      <rect x="10" y="6.2" width="12" height="19.6" rx="1.6" fill="#F2F2F7" />
      <rect x="12" y="8.4" width="8" height="2.3" rx="1" fill="#0A84FF" />
      <rect x="12" y="12.2" width="8" height="2.3" rx="1" fill="#64D2FF" />
      <rect x="12" y="16" width="8" height="2.3" rx="1" fill="#5E5CE6" />
      <circle cx="16" cy="21.6" r="1.35" fill="#30D158" />
    </Mark>
  );
}

export function CloudArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path
        fill="#0A84FF"
        d="M8.4 22.6h14.2a4.5 4.5 0 0 0 .4-9 6 6 0 0 0-11.4-1.4 4.3 4.3 0 0 0-3.2 10.4z"
      />
      <path fill="#7DD3FC" d="M11.2 21.4h9.6a3 3 0 0 0 .3-6 4.2 4.2 0 0 0-8-1 3 3 0 0 0-1.9 7z" />
    </Mark>
  );
}

export function HomelabArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path fill="#FF9F0A" d="M16 4.8 5.5 14.2V26.4h7.2v-6.4h6.6v6.4h7.2V14.2z" />
      <path fill="#C2410C" d="M16 8.2 9.2 14.2V24.2h3.5v-5.2h6.6v5.2h3.5V14.2z" />
      <rect x="14.2" y="15.4" width="3.6" height="3.4" rx="0.6" fill="#FFF7ED" />
    </Mark>
  );
}

export function ProductionArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path
        fill="#FF453A"
        d="M16 3.6c3.4 3.2 5.2 7.2 5.2 11.6V23H10.8v-7.8c0-4.4 1.8-8.4 5.2-11.6z"
      />
      <path fill="#FFD60A" d="M12.2 23.2h7.6l-1.4 2.6H13.6z" />
      <path fill="#FF9F0A" d="M13.8 25.6h4.4L16 28.2z" />
      <circle cx="16" cy="13.4" r="2" fill="#FFF" />
      <circle cx="16" cy="13.4" r="1" fill="#5E5CE6" />
    </Mark>
  );
}

export function DevelopmentArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path
        d="M12.4 11.2 7.6 16l4.8 4.8M19.6 11.2 24.4 16l-4.8 4.8M17.8 9.2l-3.6 13.6"
        fill="none"
        stroke="#5E5CE6"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Mark>
  );
}

export function StagingArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path
        fill="#BF5AF2"
        d="M12.4 5h7.2v2.4l5 10.2A5 5 0 0 1 20.2 26H11.8a5 5 0 0 1-4.4-8.4l5-10.2z"
      />
      <path
        fill="#F5D0FE"
        d="M9.2 18.4h13.6l-1.5 2.8a3.6 3.6 0 0 1-3.2 1.9h-4.2a3.6 3.6 0 0 1-3.2-1.9z"
      />
      <rect x="13.6" y="4.2" width="4.8" height="2.2" rx="0.6" fill="#E9D5FF" />
    </Mark>
  );
}

export function AiGpuArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <g fill="#C7C7CC">
        <rect x="6.2" y="12.2" width="3.2" height="1.6" rx="0.4" />
        <rect x="6.2" y="15.2" width="3.2" height="1.6" rx="0.4" />
        <rect x="6.2" y="18.2" width="3.2" height="1.6" rx="0.4" />
        <rect x="22.6" y="12.2" width="3.2" height="1.6" rx="0.4" />
        <rect x="22.6" y="15.2" width="3.2" height="1.6" rx="0.4" />
        <rect x="22.6" y="18.2" width="3.2" height="1.6" rx="0.4" />
        <rect x="12.2" y="6.2" width="1.6" height="3.2" rx="0.4" />
        <rect x="15.2" y="6.2" width="1.6" height="3.2" rx="0.4" />
        <rect x="18.2" y="6.2" width="1.6" height="3.2" rx="0.4" />
        <rect x="12.2" y="22.6" width="1.6" height="3.2" rx="0.4" />
        <rect x="15.2" y="22.6" width="1.6" height="3.2" rx="0.4" />
        <rect x="18.2" y="22.6" width="1.6" height="3.2" rx="0.4" />
      </g>
      <rect x="8.6" y="8.6" width="14.8" height="14.8" rx="3" fill="#5E5CE6" />
      <rect x="12" y="12" width="8" height="8" rx="1.6" fill="#64D2FF" />
    </Mark>
  );
}

export function DatabaseArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path
        fill="#30D158"
        d="M8 10.2c0-2.4 3.6-4.2 8-4.2s8 1.8 8 4.2v12.2c0 2.4-3.6 4.2-8 4.2s-8-1.8-8-4.2z"
      />
      <ellipse cx="16" cy="10.2" rx="8" ry="3.6" fill="#86EFAC" />
      <path
        d="M8 15.2c1.8 1.6 4.7 2.5 8 2.5s6.2-.9 8-2.5"
        fill="none"
        stroke="#DCFCE7"
        strokeWidth="1.3"
      />
      <path
        d="M8 19.6c1.8 1.6 4.7 2.5 8 2.5s6.2-.9 8-2.5"
        fill="none"
        stroke="#DCFCE7"
        strokeWidth="1.3"
      />
    </Mark>
  );
}

export function StorageArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <rect x="4.5" y="7" width="23" height="18" rx="3" fill="#636366" />
      <rect x="6.6" y="9.2" width="18.8" height="4.2" rx="1.2" fill="#0A84FF" />
      <rect x="6.6" y="14.6" width="18.8" height="4.2" rx="1.2" fill="#64D2FF" />
      <circle cx="22.4" cy="21.6" r="1.25" fill="#FF9F0A" />
      <circle cx="18.8" cy="21.6" r="1.25" fill="#30D158" />
    </Mark>
  );
}

export function NetworkArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path
        d="M10 16.2 16 10.4M16 21.6 10 16.2M16 10.4 23.2 16.2M16 21.6 23.2 16.2"
        stroke="#0A84FF"
        strokeWidth="2"
        strokeLinecap="round"
      />
      <circle cx="10" cy="16.2" r="3.1" fill="#64D2FF" />
      <circle cx="23.2" cy="16.2" r="3.1" fill="#5E5CE6" />
      <circle cx="16" cy="10.4" r="3.1" fill="#0A84FF" />
      <circle cx="16" cy="21.6" r="3.1" fill="#FF9F0A" />
    </Mark>
  );
}

export function WebArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <circle cx="16" cy="16" r="10" fill="#32ADE6" />
      <ellipse cx="16" cy="16" rx="4.2" ry="10" fill="none" stroke="#E0F2FE" strokeWidth="1.4" />
      <path
        d="M6.2 16h19.6M7.6 11.2h16.8M7.6 20.8h16.8"
        fill="none"
        stroke="#E0F2FE"
        strokeWidth="1.3"
        strokeLinecap="round"
      />
    </Mark>
  );
}

export function ContainersArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <rect x="5" y="14.5" width="14" height="11" rx="2.2" fill="#0A84FF" />
      <rect x="13" y="6.5" width="14" height="11" rx="2.2" fill="#64D2FF" />
      <path
        d="M16.2 9.2h7.6M16.2 12.2h7.6M8.2 17.4h7.6M8.2 20.4h7.6"
        stroke="#fff"
        strokeWidth="1.3"
        strokeLinecap="round"
      />
    </Mark>
  );
}

export function SecurityArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path
        fill="#30D158"
        d="M16 4.2 25.4 8v7.2c0 5.4-3.8 8.8-9.4 11.2C10.4 24 6.6 20.6 6.6 15.2V8z"
      />
      <path
        d="M12.2 15.6 15 18.4 20.2 12.6"
        fill="none"
        stroke="#fff"
        strokeWidth="2.1"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Mark>
  );
}

export function BackupArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <path fill="#FF9F0A" d="M6.2 11.2 16 6.4l9.8 4.8v11.2L16 27.2 6.2 22.4z" />
      <path fill="#FDBA74" d="M16 12.2 24.2 16.2 16 20.2 7.8 16.2z" />
      <path d="M16 20.2v7" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" />
    </Mark>
  );
}

export function DesktopArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <rect x="4.5" y="5.5" width="23" height="16" rx="2.4" fill="#636366" />
      <rect x="6.4" y="7.2" width="19.2" height="11.4" rx="1.2" fill="#0A84FF" />
      <path d="M12.2 21.6h7.6l1.4 2.6H10.8z" fill="#8E8E93" />
      <rect x="9.2" y="24.2" width="13.6" height="1.8" rx="0.9" fill="#AEAEB2" />
    </Mark>
  );
}

export function CustomArt(props: ServerArtProps) {
  return (
    <Mark {...props}>
      <rect x="4.5" y="4.5" width="10.2" height="10.2" rx="2.4" fill="#FF9F0A" />
      <rect x="17.3" y="4.5" width="10.2" height="10.2" rx="2.4" fill="#0A84FF" />
      <rect x="4.5" y="17.3" width="10.2" height="10.2" rx="2.4" fill="#BF5AF2" />
      <rect x="17.3" y="17.3" width="10.2" height="10.2" rx="2.4" fill="#30D158" />
    </Mark>
  );
}

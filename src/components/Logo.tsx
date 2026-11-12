import { memo, useState } from 'react';

function hue(s: string) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
  return h;
}

function initials(name: string) {
  const words = name.replace(/\(.*?\)|\[.*?\]/g, '').trim().split(/\s+/).filter(Boolean);
  if (!words.length) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

export const ChannelLogo = memo(function ChannelLogo({ src, name, size = 44 }: { src: string; name: string; size?: number }) {
  const [failed, setFailed] = useState(false);
  const h = hue(name);
  const style = { width: size, height: size };
  if (!src || failed) {
    return (
      <div
        className="logo logo-fallback"
        style={{ ...style, background: `linear-gradient(135deg, hsl(${h} 70% 45%), hsl(${(h + 40) % 360} 70% 35%))`, fontSize: size * 0.36 }}
      >
        {initials(name)}
      </div>
    );
  }
  return (
    <div className="logo" style={style}>
      <img src={src} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={() => setFailed(true)} />
    </div>
  );
});

import { mkdirSync } from "node:fs";
import sharp from "sharp";

mkdirSync("public/icons", { recursive: true });

const svg = (size) => `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="pan" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#fb923c"/>
      <stop offset="1" stop-color="#ea580c"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" rx="120" fill="#1e293b"/>
  <circle cx="240" cy="300" r="152" fill="url(#pan)"/>
  <rect x="136" y="140" width="208" height="48" rx="24" fill="#f97316"/>
  <rect x="160" y="78" width="160" height="72" rx="36" fill="#f97316"/>
  <rect x="216" y="45" width="80" height="48" rx="24" fill="#1e293b"/>
  <rect x="60" y="252" width="60" height="40" rx="20" fill="#1e293b"/>
  <rect x="360" y="252" width="60" height="40" rx="20" fill="#1e293b"/>
  <path d="M368 366 L480 478 L496 456 L392 350 Z" fill="#1e293b"/>
  <circle cx="490" cy="480" r="22" fill="#1e293b"/>
</svg>`;

const promises = [192, 512].map((s) =>
  sharp(Buffer.from(svg(s)))
    .resize(s, s)
    .png()
    .toFile(`public/icons/icon-${s}.png`)
);

await Promise.all(promises);
console.log("ícones gerados: public/icons/icon-192.png, public/icons/icon-512.png");
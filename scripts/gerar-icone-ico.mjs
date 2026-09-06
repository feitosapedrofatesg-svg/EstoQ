#!/usr/bin/env node
// Gera scripts/icon.ico a partir de um PNG (formato ICO com entrada PNG embutida,
// aceito pelo jpackage do Windows --icon). Uso: node gerar-icone-ico.mjs <png> <ico>
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const aqui = dirname(fileURLToPath(import.meta.url));
const pngPath = process.argv[2] || join(aqui, "..", "frontend", "public", "icons", "icon-192.png");
const icoPath = process.argv[3] || join(aqui, "EstoQ.ico");

const png = readFileSync(pngPath);
const entrada = Buffer.alloc(16);
entrada.writeUInt8(0, 0);      // largura (0 = 256)
entrada.writeUInt8(0, 1);      // altura
entrada.writeUInt8(0, 2);      // cores
entrada.writeUInt8(0, 3);      // reservado
entrada.writeUInt16LE(1, 4);   // planos
entrada.writeUInt16LE(32, 6);  // bpp
entrada.writeUInt32LE(png.length, 8);
entrada.writeUInt32LE(22, 12); // offset do PNG (6 header + 16 entry)

const cabecalho = Buffer.alloc(6);
cabecalho.writeUInt16LE(0, 0);
cabecalho.writeUInt16LE(1, 2); // tipo ícone
cabecalho.writeUInt16LE(1, 4); // 1 imagem

writeFileSync(icoPath, Buffer.concat([cabecalho, entrada, png]));
console.log("ICO gerado:", icoPath, `(${png.length + 22} bytes, PNG embutido)`);
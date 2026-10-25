// Gera wokwi/sketch.ino (arquivo único, pronto para colar no wokwi.com) a partir do
// firmware real em firmware/WineGuard, trocando config.h por wokwi/config.wokwi.h.
// Assim existe UMA única fonte de código para o hardware físico e para a simulação.
//
// Uso: node firmware/tools/build-wokwi.mjs
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const src = join(root, 'firmware', 'WineGuard')
const out = join(root, 'wokwi', 'sketch.ino')

const resolve = (name) =>
  name === 'config.h' ? join(root, 'wokwi', 'config.wokwi.h') : join(src, name)

const inline = (file) =>
  readFileSync(file, 'utf8')
    .replace(/^#pragma once\s*$/m, '')
    .replace(/^#include "([^"]+)"\s*$/gm, (_, name) =>
      `// ---------- ${name === 'config.h' ? 'config.wokwi.h' : name} ----------\n${inline(resolve(name)).trim()}\n`)

const header = `// ARQUIVO GERADO por firmware/tools/build-wokwi.mjs — edite firmware/WineGuard/ e wokwi/config.wokwi.h\n`
writeFileSync(out, header + inline(join(src, 'WineGuard.ino')))
console.log(`Gerado ${out}`)

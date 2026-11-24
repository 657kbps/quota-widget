import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import tailwindcss from '@tailwindcss/vite';
import { devtools } from '@tanstack/devtools-vite';
import { tanstackRouter } from '@tanstack/router-plugin/vite';
import viteReact from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

function resolveAppVersion(): string {
  const fromEnv = process.env.VITE_APP_VERSION?.trim();
  if (fromEnv) {
    return fromEnv.startsWith('v') ? fromEnv : `v${fromEnv}`;
  }
  try {
    const gradle = readFileSync(resolve(import.meta.dirname, '../androidApp/build.gradle.kts'), 'utf8');
    const match = gradle.match(/versionName\s*=.*?"([^"]+)"/);
    if (match?.[1]) {
      return `v${match[1]}`;
    }
  } catch {}
  return 'v1.12.0';
}

const base = process.env.VITE_BASE_PATH ?? '/quota-widget/';
const appVersion = resolveAppVersion();

const config = defineConfig({
  base,
  define: {
    __APP_VERSION__: JSON.stringify(appVersion),
  },
  resolve: {
    tsconfigPaths: true,
  },
  plugins: [devtools(), tailwindcss(), tanstackRouter({ semicolons: true }), viteReact()],
});

export default config;

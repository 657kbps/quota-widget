import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { COPY, PROVIDERS } from '@/data/content';
import { FALLBACK_RELEASE } from '@/hooks/useLatestRelease';
import { resolveLocale, resolveSystemLocale, SUPPORTED_LOCALES } from '@/lib/i18n';
import { parseGradleVersionName, resolveFallbackVersion, UNKNOWN_VERSION } from '@/lib/version';
import { useLandingStore } from '@/store/landingStore';

describe('Quota Widget landing content & i18n', () => {
  it('includes all 12 providers sorted alphabetically with valid local icon assets in all 7 locales', () => {
    expect(PROVIDERS).toHaveLength(12);
    const publicDir = resolve(process.cwd(), 'public');
    const names = PROVIDERS.map((p) => p.name.en.toLowerCase());
    expect(names).toEqual([...names].sort());

    for (const provider of PROVIDERS) {
      expect(existsSync(resolve(publicDir, provider.icon))).toBe(true);
      for (const locale of SUPPORTED_LOCALES) {
        expect(provider.name[locale].length).toBeGreaterThan(0);
        expect(provider.summary[locale].length).toBeGreaterThan(0);
      }
    }
  });

  it('includes all real screenshot assets and app/favicon icons', () => {
    const publicDir = resolve(process.cwd(), 'public');
    expect(existsSync(resolve(publicDir, 'favicon.ico'))).toBe(true);
    expect(existsSync(resolve(publicDir, 'favicon-32x32.png'))).toBe(true);
    expect(existsSync(resolve(publicDir, 'logo192.png'))).toBe(true);
    expect(existsSync(resolve(publicDir, 'logo512.png'))).toBe(true);
    expect(existsSync(resolve(publicDir, 'images/app-icon.png'))).toBe(true);
    for (const item of COPY.en.showcase.tabs) {
      expect(existsSync(resolve(publicDir, item.src))).toBe(true);
    }
  });

  it('resolves system languages accurately and falls back to English', () => {
    expect(resolveSystemLocale(['zh-CN'])).toBe('zh-Hans');
    expect(resolveSystemLocale(['zh-SG'])).toBe('zh-Hans');
    expect(resolveSystemLocale(['zh-TW'])).toBe('zh-Hant');
    expect(resolveSystemLocale(['zh-HK'])).toBe('zh-Hant');
    expect(resolveSystemLocale(['zh-Hant-TW'])).toBe('zh-Hant');
    expect(resolveSystemLocale(['ja-JP'])).toBe('ja');
    expect(resolveSystemLocale(['de-DE'])).toBe('de');
    expect(resolveSystemLocale(['fr-FR'])).toBe('fr');
    expect(resolveSystemLocale(['ru-RU'])).toBe('ru');
    expect(resolveSystemLocale(['es-ES', 'ko-KR'])).toBe('en');
    expect(resolveSystemLocale([])).toBe('en');
    expect(resolveLocale('system', ['ko-KR'])).toBe('en');
    expect(resolveLocale('ja', ['zh-CN'])).toBe('ja');
  });

  it('defaults to system preference and updates resolved locale in Zustand store', () => {
    expect(useLandingStore.getState().localePreference).toBe('system');
    useLandingStore.getState().setLocalePreference('ja');
    expect(useLandingStore.getState().locale).toBe('ja');
    useLandingStore.getState().setLocalePreference('system');
    expect(useLandingStore.getState().localePreference).toBe('system');
  });

  it('uses the version Vite injects from androidApp/build.gradle.kts', () => {
    const gradle = readFileSync(resolve(process.cwd(), '../androidApp/build.gradle.kts'), 'utf8');
    const versionName = parseGradleVersionName(gradle);
    expect(versionName).toMatch(/^\d+\.\d+\.\d+$/);
    // The define reaches both the build and this test run, so the placeholder tracks the app.
    expect(FALLBACK_RELEASE.version).toBe(`v${versionName}`);
    expect(resolveFallbackVersion('  v1.2.3  ')).toBe('v1.2.3');
  });

  it('never invents a release number when __APP_VERSION__ is missing', () => {
    expect(resolveFallbackVersion(undefined)).toBe(UNKNOWN_VERSION);
    expect(resolveFallbackVersion('')).toBe(UNKNOWN_VERSION);
    expect(resolveFallbackVersion('   ')).toBe(UNKNOWN_VERSION);
    expect(parseGradleVersionName('')).toBeUndefined();
  });
});

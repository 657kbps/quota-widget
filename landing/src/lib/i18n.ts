export type SupportedLocale = 'en' | 'zh-Hans' | 'zh-Hant' | 'ja' | 'de' | 'fr' | 'ru';
export type LocalePreference = 'system' | SupportedLocale;

export const SUPPORTED_LOCALES: readonly SupportedLocale[] = ['en', 'zh-Hans', 'zh-Hant', 'ja', 'de', 'fr', 'ru'];

export const LANGUAGE_LABELS: Record<SupportedLocale, string> = {
  'zh-Hans': '简体中文',
  'zh-Hant': '繁體中文',
  en: 'English',
  ja: '日本語',
  de: 'Deutsch',
  fr: 'Français',
  ru: 'Русский',
};

export const HTML_LANG_TAGS: Record<SupportedLocale, string> = {
  'zh-Hans': 'zh-CN',
  'zh-Hant': 'zh-TW',
  en: 'en',
  ja: 'ja',
  de: 'de',
  fr: 'fr',
  ru: 'ru',
};

function matchTag(rawTag: string): SupportedLocale | null {
  const tag = rawTag.trim().toLowerCase().replaceAll('_', '-');
  if (!tag) return null;

  if (tag === 'zh-hant' || tag.startsWith('zh-hant-') || tag === 'zh-tw' || tag === 'zh-hk' || tag === 'zh-mo') {
    return 'zh-Hant';
  }
  if (tag === 'zh' || tag.startsWith('zh-')) {
    return 'zh-Hans';
  }

  const base = tag.split('-')[0];
  if (base === 'en') return 'en';
  if (base === 'ja') return 'ja';
  if (base === 'de') return 'de';
  if (base === 'fr') return 'fr';
  if (base === 'ru') return 'ru';

  return null;
}

export function resolveSystemLocale(languages?: readonly string[]): SupportedLocale {
  const candidates =
    languages ??
    (typeof navigator !== 'undefined'
      ? navigator.languages?.length
        ? navigator.languages
        : [navigator.language]
      : []);

  for (const candidate of candidates) {
    if (!candidate) continue;
    const matched = matchTag(candidate);
    if (matched) return matched;
  }

  return 'en';
}

export function resolveLocale(preference: LocalePreference, languages?: readonly string[]): SupportedLocale {
  return preference === 'system' ? resolveSystemLocale(languages) : preference;
}

export function isLocalePreference(value: string | null): value is LocalePreference {
  return value === 'system' || (value !== null && SUPPORTED_LOCALES.includes(value as SupportedLocale));
}

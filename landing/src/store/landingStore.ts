import { create } from 'zustand';
import {
  isLocalePreference,
  type LocalePreference,
  resolveLocale,
  resolveSystemLocale,
  type SupportedLocale,
} from '@/lib/i18n';

export type ThemePreference = 'system' | 'light' | 'dark';

function detectInitialLocalePreference(): LocalePreference {
  if (typeof window === 'undefined') return 'system';
  try {
    const saved = localStorage.getItem('qw-locale');
    if (isLocalePreference(saved)) return saved;
    return 'system';
  } catch {
    return 'system';
  }
}

function detectInitialTheme(): ThemePreference {
  if (typeof window === 'undefined') return 'system';
  try {
    const saved = localStorage.getItem('qw-theme');
    if (saved === 'light' || saved === 'dark' || saved === 'system') return saved;
    return 'system';
  } catch {
    return 'system';
  }
}

interface LandingState {
  localePreference: LocalePreference;
  locale: SupportedLocale;
  theme: ThemePreference;
  setLocalePreference: (preference: LocalePreference) => void;
  refreshSystemLocale: (languages?: readonly string[]) => void;
  setTheme: (theme: ThemePreference) => void;
}

const initialPreference = detectInitialLocalePreference();

export const useLandingStore = create<LandingState>((set) => ({
  localePreference: initialPreference,
  locale: resolveLocale(initialPreference),
  theme: detectInitialTheme(),
  setLocalePreference: (localePreference) => {
    try {
      localStorage.setItem('qw-locale', localePreference);
    } catch {}
    set({
      localePreference,
      locale: resolveLocale(localePreference),
    });
  },
  refreshSystemLocale: (languages) =>
    set((state) => (state.localePreference === 'system' ? { locale: resolveSystemLocale(languages) } : state)),
  setTheme: (theme) => {
    try {
      localStorage.setItem('qw-theme', theme);
    } catch {}
    set({ theme });
  },
}));

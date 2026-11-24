import { Check, Languages, Monitor, Moon, Sun } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { GitHubIcon } from '@/components/GitHubIcon';
import { COPY, REPO_URL } from '@/data/content';
import { assetUrl } from '@/lib/assets';
import { LANGUAGE_LABELS, type LocalePreference, SUPPORTED_LOCALES } from '@/lib/i18n';
import { type ThemePreference, useLandingStore } from '@/store/landingStore';

const ORDERED_LOCALES: LocalePreference[] = ['system', ...SUPPORTED_LOCALES];

export function Navbar() {
  const { locale, localePreference, setLocalePreference, theme, setTheme } = useLandingStore();
  const t = COPY[locale];
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const handlePointerDown = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [menuOpen]);

  const nextTheme: Record<ThemePreference, ThemePreference> = {
    system: 'light',
    light: 'dark',
    dark: 'system',
  };

  const ThemeIcon = theme === 'dark' ? Moon : theme === 'light' ? Sun : Monitor;

  return (
    <header className='mx-auto flex max-w-5xl items-center justify-between px-6 py-6'>
      <a href='#top' className='flex items-center gap-2.5'>
        <img
          src={assetUrl('logo192.png')}
          alt='Quota Widget'
          className='h-7 w-7 rounded-lg bg-white p-0.5 shadow-2xs ring-1 ring-black/5'
        />
        <span className='text-sm font-semibold tracking-tight text-[var(--fg-primary)]'>{t.appName}</span>
      </a>

      <div className='flex items-center gap-1.5'>
        <div ref={menuRef} className='relative'>
          <button
            type='button'
            onClick={() => setMenuOpen((prev) => !prev)}
            aria-label='Select language'
            aria-expanded={menuOpen}
            className='inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium text-[var(--fg-secondary)] transition-colors hover:bg-[var(--bg-surface-muted)] hover:text-[var(--fg-primary)]'
          >
            <Languages className='h-3.5 w-3.5' />
            <span>{LANGUAGE_LABELS[locale]}</span>
          </button>

          {menuOpen && (
            <div
              role='menu'
              className='absolute right-0 z-50 mt-2 w-48 overflow-hidden rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-1.5 shadow-xl'
            >
              {ORDERED_LOCALES.map((option) => {
                const selected = localePreference === option;
                const label = option === 'system' ? t.systemLanguage : LANGUAGE_LABELS[option];
                return (
                  <button
                    key={option}
                    type='button'
                    role='menuitemradio'
                    aria-checked={selected}
                    onClick={() => {
                      setLocalePreference(option);
                      setMenuOpen(false);
                    }}
                    className={`flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-xs transition-colors ${
                      selected
                        ? 'bg-[var(--bg-surface-muted)] font-medium text-[var(--fg-primary)]'
                        : 'text-[var(--fg-secondary)] hover:bg-[var(--bg-surface-muted)] hover:text-[var(--fg-primary)]'
                    }`}
                  >
                    <span>{label}</span>
                    {selected && <Check className='h-3.5 w-3.5 text-[var(--accent)]' />}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <button
          type='button'
          onClick={() => setTheme(nextTheme[theme])}
          aria-label={`Theme: ${theme}`}
          className='inline-flex h-8 w-8 items-center justify-center rounded-full text-[var(--fg-secondary)] transition-colors hover:bg-[var(--bg-surface-muted)] hover:text-[var(--fg-primary)]'
        >
          <ThemeIcon className='h-4 w-4' />
        </button>

        <a
          href={REPO_URL}
          target='_blank'
          rel='noreferrer'
          aria-label='GitHub'
          className='inline-flex h-8 w-8 items-center justify-center rounded-full text-[var(--fg-secondary)] transition-colors hover:bg-[var(--bg-surface-muted)] hover:text-[var(--fg-primary)]'
        >
          <GitHubIcon className='h-4 w-4' />
        </a>
      </div>
    </header>
  );
}

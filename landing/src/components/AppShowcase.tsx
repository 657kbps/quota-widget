import { useState } from 'react';
import { COPY } from '@/data/content';
import { assetUrl } from '@/lib/assets';
import { useLandingStore } from '@/store/landingStore';

export function AppShowcase() {
  const { locale } = useLandingStore();
  const t = COPY[locale].showcase;
  const [activeId, setActiveId] = useState<'dark' | 'home' | 'light'>('home');

  return (
    <section className='border-t border-[var(--border-subtle)] py-20 sm:py-28'>
      <div className='mx-auto max-w-5xl px-6 text-center'>
        <h2 className='text-2xl font-semibold tracking-tight text-[var(--fg-primary)] sm:text-3xl'>{t.title}</h2>

        {/* Segmented pill for mobile & focus */}
        <div className='mt-6 inline-flex rounded-full border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-1'>
          {t.tabs.map((tab) => (
            <button
              key={tab.id}
              type='button'
              onClick={() => setActiveId(tab.id)}
              className={`rounded-full px-4 py-1.5 text-xs font-medium transition-colors ${
                activeId === tab.id
                  ? 'bg-[var(--fg-primary)] text-[var(--bg-canvas)]'
                  : 'text-[var(--fg-secondary)] hover:text-[var(--fg-primary)]'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Trio mockup stage: clean frames, zero paragraph copy */}
        <div className='mt-12 grid grid-cols-1 items-center gap-8 md:grid-cols-3'>
          {t.tabs.map((tab) => {
            const isActive = activeId === tab.id;
            return (
              <button
                key={tab.id}
                type='button'
                onClick={() => setActiveId(tab.id)}
                className={`group mx-auto flex flex-col items-center transition-all duration-300 ${
                  isActive ? 'scale-100 opacity-100' : 'hidden opacity-60 hover:opacity-90 md:flex md:scale-[0.94]'
                }`}
              >
                <div
                  className={`w-[240px] overflow-hidden rounded-[32px] border-[5px] border-[#1c202b] bg-[#0e1118] transition-shadow duration-300 ${
                    isActive ? 'shadow-2xl' : 'shadow-lg'
                  }`}
                >
                  <img
                    src={assetUrl(tab.src)}
                    alt={tab.label}
                    loading='lazy'
                    className='block h-auto w-full object-cover'
                  />
                </div>
                <span className='mt-4 text-xs font-medium text-[var(--fg-muted)]'>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}

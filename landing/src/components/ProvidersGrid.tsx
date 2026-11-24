import { COPY, PROVIDERS } from '@/data/content';
import { assetUrl } from '@/lib/assets';
import { useLandingStore } from '@/store/landingStore';

export function ProvidersGrid() {
  const { locale } = useLandingStore();
  const t = COPY[locale].providers;

  const sortedProviders = [...PROVIDERS].sort((a, b) =>
    a.name[locale].toLowerCase().localeCompare(b.name[locale].toLowerCase(), 'en'),
  );

  return (
    <section className='border-t border-[var(--border-subtle)] py-20 sm:py-28'>
      <div className='mx-auto max-w-5xl px-6'>
        <h2 className='text-center text-2xl font-semibold tracking-tight text-[var(--fg-primary)] sm:text-3xl'>
          {t.title}
        </h2>

        <div className='mt-12 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4'>
          {sortedProviders.map((provider) => (
            <div
              key={provider.id}
              className='flex items-center gap-3 rounded-2xl border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-4 py-3.5 transition-colors hover:border-[var(--border-strong)]'
            >
              <img
                src={assetUrl(provider.icon)}
                alt={provider.name[locale]}
                className={`h-7 w-7 shrink-0 object-contain ${provider.monochrome ? 'dark:invert' : ''}`}
              />
              <div className='min-w-0'>
                <div className='truncate text-sm font-medium text-[var(--fg-primary)]'>{provider.name[locale]}</div>
                <div className='truncate text-[11px] text-[var(--fg-muted)]'>{provider.summary[locale]}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

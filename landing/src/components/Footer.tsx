import { Palette, RefreshCw, ShieldCheck } from 'lucide-react';
import { COPY } from '@/data/content';
import { useLandingStore } from '@/store/landingStore';

const ICONS = {
  refresh: RefreshCw,
  security: ShieldCheck,
  theme: Palette,
} as const;

export function Footer() {
  const { locale } = useLandingStore();
  const t = COPY[locale];

  return (
    <footer className='border-t border-[var(--border-subtle)] py-20 sm:py-24'>
      <div className='mx-auto max-w-5xl px-6'>
        <div className='grid grid-cols-1 gap-8 sm:grid-cols-3'>
          {t.essentials.map((item) => {
            const Icon = ICONS[item.id];
            return (
              <div key={item.id} className='flex items-start gap-3'>
                <Icon className='mt-0.5 h-4 w-4 shrink-0 text-[var(--accent)]' />
                <div>
                  <h3 className='text-sm font-medium text-[var(--fg-primary)]'>{item.title}</h3>
                  <p className='mt-1 text-xs leading-relaxed text-[var(--fg-muted)]'>{item.desc}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </footer>
  );
}

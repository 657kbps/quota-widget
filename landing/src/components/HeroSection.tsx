import { ArrowUpRight, Download, Moon, RefreshCw, Sun } from 'lucide-react';
import { useState } from 'react';
import { COPY, RELEASES_URL, REPO_URL } from '@/data/content';
import { useLatestRelease } from '@/hooks/useLatestRelease';
import { useLandingStore } from '@/store/landingStore';

export function HeroSection() {
  const { locale } = useLandingStore();
  const t = COPY[locale];
  const { data: release } = useLatestRelease();

  const [showRemaining, setShowRemaining] = useState(false);
  const [capsule, setCapsule] = useState(false);
  const [widgetDark, setWidgetDark] = useState(false);
  const [spinning, setSpinning] = useState(false);
  const [synced, setSynced] = useState(false);

  const val = (used: number) => (showRemaining ? 100 - used : used);

  const triggerRefresh = () => {
    setSpinning(true);
    setTimeout(() => {
      setSpinning(false);
      setSynced(true);
    }, 500);
  };

  // Colors mirrored from Material 3 Glance widgets (widgets-light.png / widgets-dark.png)
  const cardBg = widgetDark ? '#1D2332' : '#EEF1FA';
  const onSurface = widgetDark ? '#E6E8F0' : '#1A1C24';
  const onSurfaceVariant = widgetDark ? '#A0A6B8' : '#5C6274';
  const primary = widgetDark ? '#B9C3FF' : '#4B5B96';
  const onPrimary = widgetDark ? '#182353' : '#FFFFFF';
  const surfaceVariant = widgetDark ? '#2C3242' : '#DDE1EE';
  const primaryContainer = widgetDark ? '#334173' : '#D9E0F7';
  const onPrimaryContainer = widgetDark ? '#DCE1FF' : '#1B2340';

  const capsuleTextColor = (pct: number) => (pct >= 55 ? onPrimary : onPrimaryContainer);

  return (
    <section id='top' className='relative overflow-hidden pb-24 pt-12 sm:pb-32 sm:pt-20'>
      <div
        aria-hidden='true'
        className='pointer-events-none absolute left-1/2 top-0 h-[360px] w-[680px] -translate-x-1/2 rounded-full blur-3xl'
        style={{ background: 'radial-gradient(circle, var(--glow) 0%, transparent 70%)' }}
      />

      <div className='relative mx-auto max-w-5xl px-6 text-center'>
        <h1 className='mx-auto max-w-2xl text-4xl font-semibold tracking-tight text-[var(--fg-primary)] sm:text-6xl sm:leading-[1.1]'>
          {t.hero.headline}
        </h1>

        <p className='mx-auto mt-5 max-w-lg text-base leading-relaxed text-[var(--fg-secondary)] sm:text-lg'>
          {t.hero.subhead}
        </p>

        <div className='mt-8 flex items-center justify-center gap-4'>
          <a
            href={release.apkUrl ?? RELEASES_URL}
            target='_blank'
            rel='noreferrer'
            className='inline-flex items-center gap-2 rounded-full bg-[var(--accent)] px-6 py-2.5 text-sm font-medium text-[var(--accent-fg)] transition-colors hover:bg-[var(--accent-hover)]'
          >
            <Download className='h-4 w-4' />
            <span>{t.hero.download}</span>
            <span className='opacity-75'>{release.version}</span>
          </a>

          <a
            href={REPO_URL}
            target='_blank'
            rel='noreferrer'
            className='inline-flex items-center gap-1 px-2 py-2 text-sm font-medium text-[var(--fg-secondary)] transition-colors hover:text-[var(--fg-primary)]'
          >
            <span>{t.hero.github}</span>
            <ArrowUpRight className='h-3.5 w-3.5' />
          </a>
        </div>

        {/* Authentic Glance Widget Trio */}
        <div className='mx-auto mt-16 max-w-[660px]'>
          <div className='grid grid-cols-1 items-center justify-items-center gap-4 sm:grid-cols-3'>
            {/* 1. Standard 2x2 Widget (OpenCode Go) */}
            <div
              className='flex h-[206px] w-[204px] flex-col rounded-[24px] pb-[22px] pl-[14px] pr-[14px] pt-[14px] text-left shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'
              style={{ backgroundColor: cardBg, color: onSurface }}
            >
              <div className='flex items-center justify-between'>
                <span className='truncate text-[15px] font-medium leading-tight'>OpenCode Go</span>
                <button
                  type='button'
                  onClick={triggerRefresh}
                  aria-label='Refresh OpenCode Go widget'
                  className='flex h-7 w-7 shrink-0 items-center justify-center rounded-full transition-opacity hover:opacity-70'
                  style={{ color: primary }}
                >
                  <RefreshCw className={`h-5 w-5 ${spinning ? 'animate-spin' : ''}`} />
                </button>
              </div>

              <div className='mt-auto'>
                <div className='text-[34px] font-bold leading-none tracking-tight tabular-nums'>{val(62)} %</div>

                <div
                  className={`mt-2 w-full overflow-hidden rounded-full ${capsule ? 'h-3' : 'h-[6px]'}`}
                  style={{ backgroundColor: surfaceVariant }}
                >
                  <div
                    className='h-full rounded-full transition-all duration-300'
                    style={{ width: `${val(62)}%`, backgroundColor: primary }}
                  />
                </div>

                <div className='mt-2.5 truncate text-[12px] leading-tight' style={{ color: onSurfaceVariant }}>
                  {t.widgets.resetsAt}
                </div>
              </div>
            </div>

            {/* 2. Overview 2x2 Widget (Kimi Code) */}
            <div
              className='flex h-[206px] w-[204px] flex-col rounded-[24px] pb-[22px] pl-[14px] pr-[14px] pt-[14px] text-left shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'
              style={{ backgroundColor: cardBg, color: onSurface }}
            >
              <div className='flex items-center justify-between'>
                <span className='truncate text-[15px] font-medium leading-tight'>Kimi Code</span>
                <button
                  type='button'
                  onClick={triggerRefresh}
                  aria-label='Refresh Kimi Code widget'
                  className='flex h-7 w-7 shrink-0 items-center justify-center rounded-full transition-opacity hover:opacity-70'
                  style={{ color: primary }}
                >
                  <RefreshCw className={`h-5 w-5 ${spinning ? 'animate-spin' : ''}`} />
                </button>
              </div>

              <div className='mt-auto'>
                <div className='space-y-2.5'>
                  {[
                    { label: `${t.widgets.rolling} · 1d1h`, pct: val(38) },
                    { label: `${t.widgets.weekly} · 1d23h`, pct: val(72) },
                  ].map((row) =>
                    capsule ? (
                      <div
                        key={row.label}
                        className='relative flex h-[28px] items-center justify-between overflow-hidden rounded-[8px] px-3 text-[12px] font-medium'
                        style={{ backgroundColor: primaryContainer, color: capsuleTextColor(row.pct) }}
                      >
                        <div
                          className='absolute inset-y-0 left-0 rounded-[8px] transition-all duration-300'
                          style={{ width: `${row.pct}%`, backgroundColor: primary }}
                        />
                        <span className='relative z-10 truncate pr-2'>{row.label}</span>
                        <span className='relative z-10 shrink-0 tabular-nums'>{row.pct} %</span>
                      </div>
                    ) : (
                      <div key={row.label}>
                        <div className='flex items-center justify-between text-[12px] leading-tight'>
                          <span className='truncate pr-1' style={{ color: onSurfaceVariant }}>
                            {row.label}
                          </span>
                          <span className='shrink-0 font-medium tabular-nums'>{row.pct} %</span>
                        </div>
                        <div
                          className='mt-1 h-[6px] w-full overflow-hidden rounded-full'
                          style={{ backgroundColor: surfaceVariant }}
                        >
                          <div
                            className='h-full rounded-full transition-all duration-300'
                            style={{ width: `${row.pct}%`, backgroundColor: primary }}
                          />
                        </div>
                      </div>
                    ),
                  )}
                </div>

                <div className='mt-2.5 truncate text-[12px] leading-tight' style={{ color: onSurfaceVariant }}>
                  {synced ? t.widgets.updatedNow : t.widgets.updatedAt}
                </div>
              </div>
            </div>

            {/* 3. Right Column: Two Authentic 2x1 Compact Widgets (97 + 12 + 97 = 206px) */}
            <div className='flex h-[206px] w-[204px] flex-col justify-between gap-3'>
              {/* Usage Compact 2x1 */}
              <div
                className='flex h-[97px] w-full items-center justify-between rounded-[24px] px-3 text-left shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'
                style={{ backgroundColor: cardBg, color: onSurface }}
              >
                <div className='min-w-0 flex-1 pr-2'>
                  <div className='text-[26px] font-bold leading-none tracking-tight tabular-nums'>{val(41)} %</div>
                  <div className='mt-1 truncate text-[10px] leading-tight' style={{ color: onSurfaceVariant }}>
                    {t.widgets.resetsOn}
                  </div>
                </div>
                <button
                  type='button'
                  onClick={triggerRefresh}
                  aria-label='Refresh compact usage widget'
                  className='flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-full transition-opacity hover:opacity-70'
                  style={{ color: primary }}
                >
                  <RefreshCw className={`h-6 w-6 ${spinning ? 'animate-spin' : ''}`} />
                </button>
              </div>

              {/* Balance Compact 2x1 (DeepSeek) */}
              <div
                className='flex h-[97px] w-full items-center justify-between rounded-[24px] px-3 text-left shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'
                style={{ backgroundColor: cardBg, color: onSurface }}
              >
                <div className='min-w-0 flex-1 pr-2'>
                  <div className='text-[26px] font-bold leading-none tracking-tight tabular-nums'>¥ 128.40</div>
                  <div className='mt-1 truncate text-[11px] leading-tight' style={{ color: onSurfaceVariant }}>
                    {synced ? t.widgets.updatedNow : t.widgets.updatedAt}
                  </div>
                </div>
                <button
                  type='button'
                  onClick={triggerRefresh}
                  aria-label='Refresh compact balance widget'
                  className='flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-full transition-opacity hover:opacity-70'
                  style={{ color: primary }}
                >
                  <RefreshCw className={`h-6 w-6 ${spinning ? 'animate-spin' : ''}`} />
                </button>
              </div>
            </div>
          </div>

          {/* Minimal single-line interactive toggle pill */}
          <div className='mt-6 inline-flex flex-wrap items-center justify-center gap-1 rounded-full border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-1 text-xs'>
            <button
              type='button'
              onClick={() => setShowRemaining((p) => !p)}
              className='rounded-full px-3 py-1 font-medium text-[var(--fg-secondary)] transition-colors hover:bg-[var(--bg-surface-muted)] hover:text-[var(--fg-primary)]'
            >
              {showRemaining ? t.widgets.remaining : t.widgets.used}
            </button>
            <span className='text-[var(--border-strong)]'>·</span>
            <button
              type='button'
              onClick={() => setCapsule((p) => !p)}
              className='rounded-full px-3 py-1 font-medium text-[var(--fg-secondary)] transition-colors hover:bg-[var(--bg-surface-muted)] hover:text-[var(--fg-primary)]'
            >
              {capsule ? t.widgets.capsule : t.widgets.bar}
            </button>
            <span className='text-[var(--border-strong)]'>·</span>
            <button
              type='button'
              onClick={() => setWidgetDark((p) => !p)}
              className='inline-flex items-center gap-1 rounded-full px-3 py-1 font-medium text-[var(--fg-secondary)] transition-colors hover:bg-[var(--bg-surface-muted)] hover:text-[var(--fg-primary)]'
            >
              {widgetDark ? <Moon className='h-3 w-3' /> : <Sun className='h-3 w-3' />}
              <span>{widgetDark ? t.widgets.dark : t.widgets.light}</span>
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}

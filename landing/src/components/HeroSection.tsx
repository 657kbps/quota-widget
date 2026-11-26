import { ArrowUpRight, Download, RefreshCw } from 'lucide-react';
import { SwiperDots } from '@/components/SwiperDots';
import { COPY, RELEASES_URL, REPO_URL } from '@/data/content';
import { useHorizontalSwiper } from '@/hooks/useHorizontalSwiper';
import { useLatestRelease } from '@/hooks/useLatestRelease';
import { useLandingStore } from '@/store/landingStore';

const RING_SIZE = 138;
const RING_STROKE = 10;
const RING_CENTER = RING_SIZE / 2;
const RING_RADIUS = (RING_SIZE - RING_STROKE) / 2;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;
const RING_PCT = 68;

const GAUGE_PCT = 50;
const GAUGE_START_DEG = 135;
const GAUGE_SWEEP_DEG = 270;
const GAUGE_SEGMENT_COUNT = 6;
const GAUGE_GAP_RATIO = 0.42;
const WIDGET_SLIDE_LABELS = ['OpenCode Go', 'Codex', 'Zhipu', 'Kimi Code', 'Compact'] as const;

function polarToCartesian(cx: number, cy: number, r: number, angleDeg: number) {
  const rad = (angleDeg * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}

function describeArc(cx: number, cy: number, r: number, startDeg: number, sweepDeg: number) {
  const start = polarToCartesian(cx, cy, r, startDeg);
  const end = polarToCartesian(cx, cy, r, startDeg + sweepDeg);
  const largeArc = sweepDeg > 180 ? 1 : 0;
  return `M ${start.x.toFixed(2)} ${start.y.toFixed(2)} A ${r} ${r} 0 ${largeArc} 1 ${end.x.toFixed(2)} ${end.y.toFixed(2)}`;
}

// Mirrors buildCircularArcSpec (GAUGE_SEGMENTED) in CircularProgressGeometry.kt
const GAUGE_SEGMENTS = (() => {
  const radToDeg = 180 / Math.PI;
  const gapDeg = (RING_STROKE / RING_RADIUS) * radToDeg + ((RING_STROKE * GAUGE_GAP_RATIO) / RING_RADIUS) * radToDeg;
  const arcSweep = (GAUGE_SWEEP_DEG - (GAUGE_SEGMENT_COUNT - 1) * gapDeg) / GAUGE_SEGMENT_COUNT;
  const pitch = (GAUGE_SWEEP_DEG - arcSweep) / (GAUGE_SEGMENT_COUNT - 1);
  const activeCount = Math.round((GAUGE_PCT / 100) * GAUGE_SEGMENT_COUNT);
  return Array.from({ length: GAUGE_SEGMENT_COUNT }, (_, index) => ({
    key: index,
    d: describeArc(RING_CENTER, RING_CENTER, RING_RADIUS, GAUGE_START_DEG + index * pitch, arcSweep),
    active: index < activeCount,
  }));
})();

function WidgetHeader({ title }: { title: string }) {
  return (
    <div className='flex items-center justify-between'>
      <span className='truncate text-[15px] font-medium leading-tight'>{title}</span>
      <span aria-hidden='true' className='flex h-7 w-7 shrink-0 items-center justify-center text-(--widget-primary)'>
        <RefreshCw className='h-5 w-5' />
      </span>
    </div>
  );
}

export function HeroSection() {
  const { locale } = useLandingStore();
  const t = COPY[locale];
  const { data: release } = useLatestRelease();
  const { scrollerRef, activeSlide, handleScroll, scrollToSlide } = useHorizontalSwiper(WIDGET_SLIDE_LABELS.length);

  return (
    <section id='top' className='relative overflow-hidden pb-24 pt-12 sm:pb-32 sm:pt-20'>
      <div className='relative mx-auto max-w-5xl px-6 text-center'>
        <h1 className='mx-auto max-w-2xl text-4xl font-semibold tracking-tight text-(--fg-primary) sm:text-6xl sm:leading-[1.1]'>
          {t.hero.headline}
        </h1>

        <p className='mx-auto mt-5 max-w-lg text-base leading-relaxed text-(--fg-secondary) sm:text-lg'>
          {t.hero.subhead}
        </p>

        <div className='mt-8 flex items-center justify-center gap-4'>
          <a
            href={release.apkUrl ?? RELEASES_URL}
            target='_blank'
            rel='noreferrer'
            className='inline-flex items-center gap-2 rounded-full bg-(--accent) px-6 py-2.5 text-sm font-medium text-(--accent-fg) transition-colors hover:bg-(--accent-hover)'
          >
            <Download className='h-4 w-4' />
            <span>{t.hero.download}</span>
            <span className='opacity-75'>{release.version}</span>
          </a>

          <a
            href={REPO_URL}
            target='_blank'
            rel='noreferrer'
            className='inline-flex items-center gap-1 px-2 py-2 text-sm font-medium text-(--fg-secondary) transition-colors hover:text-(--fg-primary)'
          >
            <span>{t.hero.github}</span>
            <ArrowUpRight className='h-3.5 w-3.5' />
          </a>
        </div>

        {/* Authentic Glance Widget Showcase: 1-card horizontal swiper on mobile, centered 2-row flex grid on sm+ */}
        <div className='mt-16'>
          <div
            ref={scrollerRef}
            onScroll={handleScroll}
            className='scrollbar-none -my-4 flex snap-x snap-mandatory overflow-x-auto py-4 [&::-webkit-scrollbar]:hidden sm:mx-auto sm:my-0 sm:max-w-106 sm:flex-wrap sm:justify-center sm:gap-4 sm:overflow-visible sm:py-0 md:max-w-161'
          >
            {/* 1. Standard 2x2 Widget — Bar (OpenCode Go) */}
            <div className='flex w-full shrink-0 snap-center snap-always justify-center sm:w-auto sm:shrink'>
              <div className='flex h-51.5 w-51 flex-col rounded-3xl bg-(--widget-card-bg) px-3.5 pb-5.5 pt-3.5 text-left text-(--widget-on-surface) shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'>
                <WidgetHeader title='OpenCode Go' />

                <div className='mt-auto'>
                  <div className='text-[34px] font-bold leading-none tracking-tight tabular-nums'>62 %</div>

                  <div className='mt-2 h-1.5 w-full overflow-hidden rounded-full bg-(--widget-track)'>
                    <div className='h-full rounded-full bg-(--widget-primary)' style={{ width: '62%' }} />
                  </div>

                  <div className='mt-2.5 truncate text-[12px] leading-tight text-(--widget-on-surface-variant)'>
                    {t.widgets.resetsAt}
                  </div>
                </div>
              </div>
            </div>

            {/* 2. Standard 2x2 Widget — Ring (Codex) */}
            <div className='flex w-full shrink-0 snap-center snap-always justify-center sm:w-auto sm:shrink'>
              <div className='flex h-51.5 w-51 flex-col rounded-3xl bg-(--widget-card-bg) p-3.5 text-left text-(--widget-on-surface) shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'>
                <WidgetHeader title='Codex' />

                <div className='flex flex-1 items-center justify-center'>
                  <div className='relative h-34.5 w-34.5'>
                    <svg viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`} className='h-full w-full' aria-hidden='true'>
                      <circle
                        cx={RING_CENTER}
                        cy={RING_CENTER}
                        r={RING_RADIUS}
                        fill='none'
                        stroke='var(--widget-track)'
                        strokeWidth={RING_STROKE}
                      />
                      <circle
                        cx={RING_CENTER}
                        cy={RING_CENTER}
                        r={RING_RADIUS}
                        fill='none'
                        stroke='var(--widget-primary)'
                        strokeWidth={RING_STROKE}
                        strokeLinecap='round'
                        transform={`rotate(-90 ${RING_CENTER} ${RING_CENTER})`}
                        strokeDasharray={RING_CIRCUMFERENCE}
                        strokeDashoffset={RING_CIRCUMFERENCE * (1 - RING_PCT / 100)}
                      />
                    </svg>
                    <span className='absolute inset-x-4.5 top-1/2 -translate-y-[calc(50%+6px)] text-center text-[28px] font-bold leading-none tracking-tight tabular-nums'>
                      {RING_PCT} %
                    </span>
                    <span className='absolute inset-x-6.5 top-[calc(50%+12px)] truncate text-center text-[10.5px] leading-tight text-(--widget-on-surface-variant)'>
                      {t.widgets.circularResetsAt}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* 3. Standard 2x2 Widget — Segmented Gauge (Zhipu) */}
            <div className='flex w-full shrink-0 snap-center snap-always justify-center sm:w-auto sm:shrink'>
              <div className='flex h-51.5 w-51 flex-col rounded-3xl bg-(--widget-card-bg) p-3.5 text-left text-(--widget-on-surface) shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'>
                <WidgetHeader title='Zhipu' />

                <div className='flex flex-1 items-center justify-center'>
                  <div className='relative h-34.5 w-34.5'>
                    <svg viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`} className='h-full w-full' aria-hidden='true'>
                      {GAUGE_SEGMENTS.map((seg) => (
                        <path
                          key={seg.key}
                          d={seg.d}
                          fill='none'
                          stroke={seg.active ? 'var(--widget-primary)' : 'var(--widget-track)'}
                          strokeWidth={RING_STROKE}
                          strokeLinecap='round'
                        />
                      ))}
                    </svg>
                    <span className='absolute inset-x-4.5 top-1/2 -translate-y-[calc(50%+6px)] text-center text-[28px] font-bold leading-none tracking-tight tabular-nums'>
                      {GAUGE_PCT} %
                    </span>
                    <span className='absolute inset-x-5.5 top-[calc(50%+12px)] truncate text-center text-[10.5px] leading-tight text-(--widget-on-surface-variant)'>
                      {t.widgets.circularUpdatedAt}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* 4. Overview 2x2 Widget — Capsule Bars (Kimi Code) */}
            <div className='flex w-full shrink-0 snap-center snap-always justify-center sm:w-auto sm:shrink'>
              <div className='flex h-51.5 w-51 flex-col rounded-3xl bg-(--widget-card-bg) px-3.5 pb-5.5 pt-3.5 text-left text-(--widget-on-surface) shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'>
                <WidgetHeader title='Kimi Code' />

                <div className='mt-auto'>
                  <div className='space-y-2.5'>
                    {[
                      { label: `${t.widgets.rolling} · 1d1h`, pct: 38 },
                      { label: `${t.widgets.weekly} · 1d23h`, pct: 72 },
                    ].map((row) => (
                      <div
                        key={row.label}
                        className='relative flex h-7 items-center justify-between overflow-hidden rounded-lg bg-(--widget-surface) px-3 text-[12px] text-(--widget-on-surface)'
                      >
                        <div
                          className='absolute inset-y-0 left-0 bg-(--widget-capsule-fill)'
                          style={{ width: `${row.pct}%` }}
                        />
                        <span className='relative z-10 truncate pr-2 font-medium'>{row.label}</span>
                        <span className='relative z-10 shrink-0 font-bold tabular-nums'>{row.pct} %</span>
                      </div>
                    ))}
                  </div>

                  <div className='mt-2.5 truncate text-[12px] leading-tight text-(--widget-on-surface-variant)'>
                    {t.widgets.updatedAt}
                  </div>
                </div>
              </div>
            </div>

            {/* 5. Two Authentic 2x1 Compact Widgets (97 + 12 + 97 = 206px) */}
            <div className='flex w-full shrink-0 snap-center snap-always justify-center sm:w-auto sm:shrink'>
              <div className='flex h-51.5 w-51 flex-col justify-between gap-3'>
                {/* Usage Compact 2x1 */}
                <div className='flex h-24.25 w-full items-center justify-between rounded-3xl bg-(--widget-card-bg) px-3 text-left text-(--widget-on-surface) shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'>
                  <div className='min-w-0 flex-1 pr-2'>
                    <div className='text-[26px] font-bold leading-none tracking-tight tabular-nums'>41 %</div>
                    <div className='mt-1 truncate text-[10px] leading-tight text-(--widget-on-surface-variant)'>
                      {t.widgets.resetsOn}
                    </div>
                  </div>
                  <span
                    aria-hidden='true'
                    className='flex h-8.5 w-8.5 shrink-0 items-center justify-center text-(--widget-primary)'
                  >
                    <RefreshCw className='h-6 w-6' />
                  </span>
                </div>

                {/* Balance Compact 2x1 (DeepSeek) */}
                <div className='flex h-24.25 w-full items-center justify-between rounded-3xl bg-(--widget-card-bg) px-3 text-left text-(--widget-on-surface) shadow-lg ring-1 ring-black/5 transition-colors duration-200 dark:ring-white/8'>
                  <div className='min-w-0 flex-1 pr-2'>
                    <div className='text-[26px] font-bold leading-none tracking-tight tabular-nums'>¥ 128.40</div>
                    <div className='mt-1 truncate text-[11px] leading-tight text-(--widget-on-surface-variant)'>
                      {t.widgets.circularUpdatedAt}
                    </div>
                  </div>
                  <span
                    aria-hidden='true'
                    className='flex h-8.5 w-8.5 shrink-0 items-center justify-center text-(--widget-primary)'
                  >
                    <RefreshCw className='h-6 w-6' />
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Mobile swiper pagination dots */}
          <SwiperDots
            labels={WIDGET_SLIDE_LABELS}
            activeSlide={activeSlide}
            onSelect={scrollToSlide}
            className='sm:hidden'
          />
        </div>
      </div>
    </section>
  );
}

import { SwiperDots } from '@/components/SwiperDots';
import { COPY } from '@/data/content';
import { useHorizontalSwiper } from '@/hooks/useHorizontalSwiper';
import { assetUrl } from '@/lib/assets';
import { useLandingStore } from '@/store/landingStore';

export function AppShowcase() {
  const { locale } = useLandingStore();
  const t = COPY[locale].showcase;
  const { scrollerRef, activeSlide, handleScroll, scrollToSlide } = useHorizontalSwiper(t.items.length);

  return (
    <section className='border-t border-(--border-subtle) py-20 sm:py-28'>
      <div className='mx-auto max-w-5xl px-6 text-center'>
        <h2 className='text-2xl font-semibold tracking-tight text-(--fg-primary) sm:text-3xl'>{t.title}</h2>

        {/* 1-card horizontal swiper below md, 3-column static stage on md+ */}
        <div className='mt-12'>
          <div
            ref={scrollerRef}
            onScroll={handleScroll}
            className='scrollbar-none -my-6 flex snap-x snap-mandatory overflow-x-auto py-6 [&::-webkit-scrollbar]:hidden md:my-0 md:grid md:grid-cols-3 md:items-center md:gap-8 md:overflow-visible md:py-0'
          >
            {t.items.map((item) => (
              <figure
                key={item.id}
                className='mx-auto flex w-full shrink-0 snap-center snap-always flex-col items-center md:w-auto md:shrink'
              >
                <div className='w-60 overflow-hidden rounded-4xl border-[5px] border-[#1c202b] bg-[#0e1118] shadow-xl'>
                  <img
                    src={assetUrl(item.src)}
                    alt={item.label}
                    loading='lazy'
                    className='block h-auto w-full object-cover'
                  />
                </div>
                <figcaption className='mt-4 text-xs font-medium text-(--fg-muted)'>{item.label}</figcaption>
              </figure>
            ))}
          </div>

          <SwiperDots
            labels={t.items.map((item) => item.label)}
            activeSlide={activeSlide}
            onSelect={scrollToSlide}
            className='md:hidden'
          />
        </div>
      </div>
    </section>
  );
}

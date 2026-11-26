import { useRef, useState } from 'react';

export function useHorizontalSwiper(slideCount: number) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const [activeSlide, setActiveSlide] = useState(0);

  const handleScroll = () => {
    const el = scrollerRef.current;
    if (!el || el.clientWidth === 0) return;
    const next = Math.round(el.scrollLeft / el.clientWidth);
    const clamped = Math.max(0, Math.min(slideCount - 1, next));
    setActiveSlide(clamped);
  };

  const scrollToSlide = (index: number) => {
    const el = scrollerRef.current;
    if (!el) return;
    setActiveSlide(index);
    el.scrollTo({ left: index * el.clientWidth, behavior: 'smooth' });
  };

  return { scrollerRef, activeSlide, handleScroll, scrollToSlide };
}

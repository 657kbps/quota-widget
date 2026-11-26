interface SwiperDotsProps {
  labels: readonly string[];
  activeSlide: number;
  onSelect: (index: number) => void;
  className?: string;
}

export function SwiperDots({ labels, activeSlide, onSelect, className = '' }: SwiperDotsProps) {
  return (
    <div className={`mt-5 flex items-center justify-center gap-1.5 ${className}`}>
      {labels.map((label, index) => {
        const active = activeSlide === index;
        return (
          <button
            key={label}
            type='button'
            aria-label={label}
            aria-current={active ? 'true' : undefined}
            onClick={() => onSelect(index)}
            className={`h-1.5 rounded-full transition-all duration-200 ${
              active ? 'w-4 bg-(--accent)' : 'w-1.5 bg-(--border-strong)'
            }`}
          />
        );
      })}
    </div>
  );
}

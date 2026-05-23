/* ------------------------------------------------------------------
 * Carousel - generic horizontal slider.
 * Each child = one slide. Slides span the full viewport width.
 *
 * Props:
 *   autoPlay     boolean   default false
 *   interval     ms        default 5000
 *   loop         boolean   default true
 *   showDots     boolean   default true
 *   showArrows   boolean   default true
 *   aspectRatio  string    e.g. "4 / 3" applied to wrapper (optional)
 *   ariaLabel    string    accessible name
 *
 * Pauses autoplay on hover/focus. Wraps with role="region".
 * ------------------------------------------------------------------ */
import { useEffect, useState, useCallback, Children } from 'react'
import clsx from 'clsx'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import IconButton from '../IconButton/IconButton.jsx'
import './Carousel.css'

export default function Carousel({
  children,
  autoPlay = false,
  interval = 5000,
  loop = true,
  showDots = true,
  showArrows = true,
  aspectRatio,
  ariaLabel = 'Carousel',
  className,
}) {
  const slides = Children.toArray(children)
  const count = slides.length
  const [idx, setIdx] = useState(0)
  const [paused, setPaused] = useState(false)

  const goTo = useCallback(
    (next) => {
      setIdx(() => {
        if (next < 0) return loop ? count - 1 : 0
        if (next >= count) return loop ? 0 : count - 1
        return next
      })
    },
    [count, loop],
  )

  const next = useCallback(() => setIdx((i) => (i + 1 >= count ? (loop ? 0 : i) : i + 1)), [count, loop])
  const prev = useCallback(() => setIdx((i) => (i - 1 < 0 ? (loop ? count - 1 : 0) : i - 1)), [count, loop])

  useEffect(() => {
    if (!autoPlay || paused || count <= 1) return
    const t = setInterval(next, interval)
    return () => clearInterval(t)
  }, [autoPlay, paused, interval, count, next])

  if (count === 0) return null

  return (
    <div
      className={clsx('ui-carousel', className)}
      style={aspectRatio ? { aspectRatio } : undefined}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      role="region"
      aria-roledescription="carousel"
      aria-label={ariaLabel}
    >
      <div className="ui-carousel__viewport">
        <div
          className="ui-carousel__track"
          style={{ transform: `translateX(-${idx * 100}%)` }}
        >
          {slides.map((s, i) => (
            <div
              key={i}
              className="ui-carousel__slide"
              aria-hidden={i !== idx}
              role="group"
              aria-roledescription="slide"
              aria-label={`${i + 1} of ${count}`}
            >
              {s}
            </div>
          ))}
        </div>
      </div>

      {showArrows && count > 1 && (
        <>
          <IconButton
            shape="round"
            aria-label="Previous slide"
            className="ui-carousel__arrow ui-carousel__arrow--prev"
            onClick={prev}
          >
            <ChevronLeft />
          </IconButton>
          <IconButton
            shape="round"
            aria-label="Next slide"
            className="ui-carousel__arrow ui-carousel__arrow--next"
            onClick={next}
          >
            <ChevronRight />
          </IconButton>
        </>
      )}

      {showDots && count > 1 && (
        <div className="ui-carousel__dots" role="tablist" aria-label="Slides">
          {slides.map((_, i) => (
            <button
              key={i}
              type="button"
              role="tab"
              aria-selected={i === idx}
              aria-label={`Go to slide ${i + 1}`}
              className={clsx('ui-carousel__dot', i === idx && 'is-active')}
              onClick={() => goTo(i)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

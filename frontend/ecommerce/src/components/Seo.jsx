/**
 * <Seo /> — page-level metadata.
 *
 * React 19 natively hoists <title>, <meta>, <link>, and <script> placed
 * inside any component into the document <head>, so no react-helmet
 * dependency is needed.
 *
 * Usage:
 *   <Seo
 *     title="Sugar-free snacks made with nuts & seeds"
 *     description="..."
 *     path="/products"
 *     image="/og/products.jpg"
 *     jsonLd={[ ... ]}
 *     noindex={false}
 *   />
 */
import { useLocation } from 'react-router-dom'
import { absoluteUrl, BRAND } from '../lib/seo'

export default function Seo({
  title,
  description,
  path,
  image,
  type = 'website',
  noindex = false,
  jsonLd = [],
  keywords,
}) {
  const location = useLocation()
  const effectivePath = path ?? location.pathname + (location.search || '')
  const url = absoluteUrl(effectivePath)
  const fullTitle = title
    ? `${title} | ${BRAND.name}`
    : `${BRAND.name} — ${BRAND.tagline}`
  const desc = description || BRAND.description
  const ogImage = image
    ? image.startsWith('http')
      ? image
      : absoluteUrl(image)
    : BRAND.defaultOgImage
  const robots = noindex
    ? 'noindex, nofollow'
    : 'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1'

  const blocks = Array.isArray(jsonLd) ? jsonLd.filter(Boolean) : [jsonLd].filter(Boolean)

  return (
    <>
      <title>{fullTitle}</title>
      <meta name="description" content={desc} />
      {keywords && <meta name="keywords" content={Array.isArray(keywords) ? keywords.join(', ') : keywords} />}
      <meta name="robots" content={robots} />
      <link rel="canonical" href={url} />

      {/* Open Graph */}
      <meta property="og:site_name" content={BRAND.name} />
      <meta property="og:type" content={type} />
      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={desc} />
      <meta property="og:url" content={url} />
      <meta property="og:image" content={ogImage} />
      <meta property="og:locale" content="en_IN" />

      {/* Twitter */}
      <meta name="twitter:card" content="summary_large_image" />
      <meta name="twitter:title" content={fullTitle} />
      <meta name="twitter:description" content={desc} />
      <meta name="twitter:image" content={ogImage} />

      {blocks.map((obj, i) => (
        <script
          key={i}
          type="application/ld+json"
          // eslint-disable-next-line react/no-danger
          dangerouslySetInnerHTML={{ __html: JSON.stringify(obj) }}
        />
      ))}
    </>
  )
}

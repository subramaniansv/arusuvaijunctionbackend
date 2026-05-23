/**
 * Contact page — info strip on the left, message form on the right.
 *
 * Form posts to POST /api/contact (public). Admin sees submissions
 * in /admin/messages.
 */
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Mail, Phone, MapPin, MessageCircle, Send, CheckCircle2 } from 'lucide-react'

import {
  Button, Input, Textarea, Alert,
} from '../components'
import Seo from '../components/Seo'
import { breadcrumbLd, localBusinessLd } from '../lib/seo'
import { useSubmitContact } from '../lib/contact'
import { useAuthStore } from '../stores/authStore'
import './StaticPage.css'

const schema = z.object({
  name:    z.string().min(2, 'Please tell us your name').max(120),
  email:   z.string().email('Please enter a valid email'),
  phone:   z.string().max(40).optional().or(z.literal('')),
  subject: z.string().max(200).optional().or(z.literal('')),
  message: z.string().min(10, 'Please add a bit more detail').max(5000),
})

const CONTACT_INFO = [
  { icon: Phone,         label: 'Call us',  value: '+91 98765 43210', href: 'tel:+919876543210' },
  { icon: MessageCircle, label: 'WhatsApp', value: '+91 98765 43210', href: 'https://wa.me/919876543210' },
  { icon: Mail,          label: 'Email',    value: 'hello@arusuvai.in', href: 'mailto:hello@arusuvai.in' },
  { icon: MapPin,        label: 'Visit us', value: 'T. Nagar, Chennai 600017' },
]

export default function Contact() {
  const user = useAuthStore((s) => s.user)
  const [submitted, setSubmitted] = useState(false)
  const [serverError, setServerError] = useState('')
  const submit = useSubmitContact()

  const {
    register, handleSubmit, reset, formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: {
      name:    user?.name || '',
      email:   user?.email || '',
      phone:   '',
      subject: '',
      message: '',
    },
  })

  const onSubmit = async (values) => {
    setServerError('')
    try {
      await submit.mutateAsync(values)
      setSubmitted(true)
      reset({ ...values, message: '', subject: '' })
    } catch (e) {
      setServerError(e?.response?.data?.message || 'Could not send your message, please try again.')
    }
  }

  return (
    <div className="staticpage">
      <Seo
        title="Contact Arusuvai Junction — Tirunelveli, Tamil Nadu"
        description="Get in touch with Arusuvai Junction for orders, bulk enquiries or feedback. Based in Tirunelveli, Tamil Nadu. We reply within one working day."
        path="/contact"
        jsonLd={[
          localBusinessLd(),
          breadcrumbLd([
            { name: 'Home', path: '/' },
            { name: 'Contact', path: '/contact' },
          ]),
        ]}
      />
      <header className="staticpage__hero">
        <span className="staticpage__eyebrow">Contact</span>
        <h1 className="staticpage__title">We&apos;d love to hear from you</h1>
        <p className="staticpage__lead">
          Questions about an order, bulk requests for a wedding or office, or just want to say
          hi — we&apos;re a message away. We try to reply within one working day.
        </p>
      </header>

      <div className="staticpage__body staticpage__body--split">
        <form className="contactform" onSubmit={handleSubmit(onSubmit)} noValidate>
          {submitted && (
            <Alert variant="success" icon={<CheckCircle2 size={18} />}>
              Thanks! Your message is with us — we&apos;ll be in touch shortly.
            </Alert>
          )}
          {serverError && <Alert variant="danger">{serverError}</Alert>}

          <div className="contactform__row contactform__row--2">
            <Input
              label="Your name"
              placeholder="e.g. Priya Ramesh"
              autoComplete="name"
              error={errors.name?.message}
              {...register('name')}
            />
            <Input
              label="Email"
              type="email"
              placeholder="you@example.com"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email')}
            />
          </div>

          <div className="contactform__row contactform__row--2">
            <Input
              label="Phone (optional)"
              type="tel"
              placeholder="+91 ..."
              autoComplete="tel"
              error={errors.phone?.message}
              {...register('phone')}
            />
            <Input
              label="Subject (optional)"
              placeholder="What's this about?"
              error={errors.subject?.message}
              {...register('subject')}
            />
          </div>

          <Textarea
            label="Message"
            rows={6}
            placeholder="Tell us what's on your mind..."
            error={errors.message?.message}
            {...register('message')}
          />

          <div className="contactform__actions">
            <Button
              type="submit"
              size="lg"
              rightIcon={<Send size={16} />}
              disabled={isSubmitting || submit.isPending}
            >
              {isSubmitting || submit.isPending ? 'Sending...' : 'Send message'}
            </Button>
          </div>
        </form>

        <aside className="staticpage__aside">
          <div className="staticpage__card">
            <h3>Reach us directly</h3>
            {CONTACT_INFO.map(({ icon: Icon, label, value, href }) => {
              const inner = (
                <>
                  <Icon size={18} aria-hidden="true" />
                  <span>
                    <div style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)' }}>{label}</div>
                    <div style={{ fontWeight: 'var(--font-weight-medium)' }}>{value}</div>
                  </span>
                </>
              )
              return href ? (
                <a key={label} href={href} className="staticpage__contact-row" target={href.startsWith('http') ? '_blank' : undefined} rel="noreferrer">
                  {inner}
                </a>
              ) : (
                <div key={label} className="staticpage__contact-row">{inner}</div>
              )
            })}
          </div>

          <div className="staticpage__card">
            <h3>Hours</h3>
            <p style={{ margin: 0 }}>
              Mon – Sat, 9:00 AM – 7:00 PM IST. We close on national holidays.
            </p>
          </div>
        </aside>
      </div>
    </div>
  )
}

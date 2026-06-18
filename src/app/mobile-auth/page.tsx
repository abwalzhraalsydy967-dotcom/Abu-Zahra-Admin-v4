'use client'

import { useEffect, useState, useCallback } from 'react'

// ─── Google Identity Services types ───────────────────────────────────────────

interface GISTokenResponse {
  access_token?: string
  id_token?: string
  error?: string
  error_description?: string
}

interface GISOAuth2 {
  initTokenClient: (config: {
    client_id: string
    scope: string
    callback: (response: GISTokenResponse) => void
    error_callback?: (error: unknown) => void
  }) => {
    requestAccessToken: (overrideConfig?: Record<string, unknown>) => void
  }
}

// ─── Constants ────────────────────────────────────────────────────────────────

const GOOGLE_CLIENT_ID =
  process.env.NEXT_PUBLIC_FIREBASE_WEB_CLIENT_ID ||
  '159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com'
const REDIRECT_SCHEME = 'abuzahra'

// ─── Page Component ───────────────────────────────────────────────────────────

export default function MobileAuthPage() {
  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [errorMessage, setErrorMessage] = useState<string>('')

  const handleSignIn = useCallback(async (idToken: string, email: string) => {
    setStatus('loading')
    setErrorMessage('')

    try {
      const res = await fetch('/api/auth/mobile-callback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken, email }),
      })

      const data = (await res.json()) as Record<string, unknown>

      if (data.ok && data.token) {
        setStatus('success')

        const token = data.token as string
        const emailParam = (data.email as string) || email
        const username = (data.username as string) || (data.display_name as string) || email.split('@')[0]

        // Redirect back to the Android app
        const redirectUrl = `${REDIRECT_SCHEME}://auth-callback?token=${encodeURIComponent(token)}&email=${encodeURIComponent(emailParam)}&username=${encodeURIComponent(username)}`

        window.location.href = redirectUrl
      } else {
        setStatus('error')
        setErrorMessage(
          (data.message as string) || 'فشل تسجيل الدخول، يرجى المحاولة مرة أخرى'
        )
      }
    } catch {
      setStatus('error')
      setErrorMessage('تعذر الاتصال بالخادم، تحقق من اتصال الإنترنت')
    }
  }, [])

  const signInWithGoogle = useCallback(() => {
    if (status === 'loading') return

    setStatus('loading')
    setErrorMessage('')

    // Load GIS script
    const w = window as unknown as { google: { accounts: { oauth2: GISOAuth2 } } }

    if (!w.google?.accounts?.oauth2) {
      const script = document.createElement('script')
      script.src = 'https://accounts.google.com/gsi/client'
      script.async = true
      script.defer = true

      script.onload = () => {
        try {
          const googleWin = window as unknown as {
            google: { accounts: { oauth2: GISOAuth2 } }
          }
          initAndRequest(googleWin)
        } catch {
          setStatus('error')
          setErrorMessage('فشل تهيئة خدمة Google، يرجى المحاولة مرة أخرى')
        }
      }

      script.onerror = () => {
        setStatus('error')
        setErrorMessage('فشل تحميل خدمة Google، تحقق من اتصال الإنترنت')
      }

      document.head.appendChild(script)
    } else {
      initAndRequest(w)
    }

    function initAndRequest(
      win: { google: { accounts: { oauth2: GISOAuth2 } } }
    ) {
      const tokenClient = win.google.accounts.oauth2.initTokenClient({
        client_id: GOOGLE_CLIENT_ID,
        scope: 'openid email profile',
        callback: async (response: GISTokenResponse) => {
          if (response.error) {
            setStatus('error')
            setErrorMessage(
              response.error_description || response.error || 'خطأ في مصادقة Google'
            )
            return
          }

          if (!response.id_token) {
            setStatus('error')
            setErrorMessage('لم يتم استلام رمز المعرّف من Google')
            return
          }

          // Decode the JWT to extract the email without a library
          try {
            const payload = JSON.parse(
              atob(response.id_token.split('.')[1])
            )
            const email = payload.email || ''
            if (!email) {
              setStatus('error')
              setErrorMessage('لم يتم العثور على البريد الإلكتروني في بيانات Google')
              return
            }
            await handleSignIn(response.id_token, email)
          } catch {
            setStatus('error')
            setErrorMessage('فشل قراءة بيانات Google')
          }
        },
        error_callback: () => {
          setStatus('error')
          setErrorMessage('تم إلغاء تسجيل الدخول أو حدث خطأ')
        },
      })

      tokenClient.requestAccessToken()
    }
  }, [status, handleSignIn])

  // Reset body styles for a clean standalone page
  useEffect(() => {
    document.body.style.margin = '0'
    document.body.style.padding = '0'
    document.body.style.minHeight = '100dvh'
    document.body.style.background =
      'linear-gradient(160deg, #022c22 0%, #064e3b 40%, #0f766e 70%, #134e4a 100%)'
    document.body.style.fontFamily = "'Cairo', 'Segoe UI', Tahoma, sans-serif"
    document.body.style.display = 'flex'
    document.body.style.alignItems = 'center'
    document.body.style.justifyContent = 'center'
    document.body.style.overflow = 'auto'

    return () => {
      document.body.style.margin = ''
      document.body.style.padding = ''
      document.body.style.minHeight = ''
      document.body.style.background = ''
      document.body.style.fontFamily = ''
      document.body.style.display = ''
      document.body.style.alignItems = ''
      document.body.style.justifyContent = ''
      document.body.style.overflow = ''
    }
  }, [])

  return (
    <div
      dir="rtl"
      style={{
        width: '100%',
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px 16px',
        boxSizing: 'border-box',
      }}
    >
      {/* Google Identity Services script preload */}
      <link
        rel="preconnect"
        href="https://accounts.google.com"
        crossOrigin="anonymous"
      />

      <div
        style={{
          width: '100%',
          maxWidth: '400px',
          background: 'rgba(255, 255, 255, 0.07)',
          backdropFilter: 'blur(24px)',
          WebkitBackdropFilter: 'blur(24px)',
          borderRadius: '24px',
          border: '1px solid rgba(255, 255, 255, 0.12)',
          padding: '48px 32px',
          textAlign: 'center',
          boxShadow: '0 25px 50px rgba(0, 0, 0, 0.3)',
        }}
      >
        {/* App Icon */}
        <div
          style={{
            width: '80px',
            height: '80px',
            margin: '0 auto 24px',
            borderRadius: '20px',
            background: 'linear-gradient(135deg, #10b981, #14b8a6)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 8px 24px rgba(16, 185, 129, 0.3)',
          }}
        >
          <svg
            width="40"
            height="40"
            viewBox="0 0 24 24"
            fill="none"
            stroke="white"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M12 2L2 7l10 5 10-5-10-5z" />
            <path d="M2 17l10 5 10-5" />
            <path d="M2 12l10 5 10-5" />
          </svg>
        </div>

        {/* App Name */}
        <h1
          style={{
            color: '#ffffff',
            fontSize: '28px',
            fontWeight: '700',
            margin: '0 0 8px',
            lineHeight: '1.4',
            letterSpacing: '0',
          }}
        >
          أبو زهرة
        </h1>

        <p
          style={{
            color: 'rgba(255, 255, 255, 0.6)',
            fontSize: '15px',
            margin: '0 0 40px',
            fontWeight: '400',
          }}
        >
          سجّل الدخول للمتابعة
        </p>

        {/* Status Messages */}
        {status === 'success' && (
          <div
            style={{
              background: 'rgba(16, 185, 129, 0.15)',
              border: '1px solid rgba(16, 185, 129, 0.3)',
              borderRadius: '12px',
              padding: '14px 16px',
              marginBottom: '24px',
              color: '#6ee7b7',
              fontSize: '14px',
              display: 'flex',
              alignItems: 'center',
            justifyContent: 'center',
              gap: '8px',
            }}
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M20 6L9 17l-5-5" />
            </svg>
            جارٍ التحويل إلى التطبيق...
          </div>
        )}

        {status === 'error' && (
          <div
            style={{
              background: 'rgba(239, 68, 68, 0.12)',
              border: '1px solid rgba(239, 68, 68, 0.25)',
              borderRadius: '12px',
              padding: '14px 16px',
              marginBottom: '24px',
              color: '#fca5a5',
              fontSize: '14px',
              lineHeight: '1.6',
            }}
          >
            {errorMessage}
          </div>
        )}

        {/* Google Sign-In Button */}
        {status !== 'success' && (
          <button
            onClick={signInWithGoogle}
            disabled={status === 'loading'}
            style={{
              width: '100%',
              height: '52px',
              borderRadius: '14px',
              border: '1px solid rgba(255, 255, 255, 0.15)',
              background: status === 'loading'
                ? 'rgba(255, 255, 255, 0.08)'
                : 'rgba(255, 255, 255, 0.1)',
              color: '#ffffff',
              fontSize: '16px',
              fontWeight: '600',
              cursor: status === 'loading' ? 'wait' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '12px',
              transition: 'all 0.2s ease',
              fontFamily: 'inherit',
              padding: '0',
              outline: 'none',
              WebkitTapHighlightColor: 'transparent',
            }}
            onMouseEnter={(e) => {
              if (status !== 'loading') {
                e.currentTarget.style.background = 'rgba(255, 255, 255, 0.16)'
                e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.25)'
              }
            }}
            onMouseLeave={(e) => {
              if (status !== 'loading') {
                e.currentTarget.style.background = 'rgba(255, 255, 255, 0.1)'
                e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.15)'
              }
            }}
          >
            {status === 'loading' ? (
              /* Spinner */
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                style={{
                  animation: 'spin 1s linear infinite',
                }}
              >
                <path d="M21 12a9 9 0 1 1-6.219-8.56" />
                <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
              </svg>
            ) : (
              /* Google "G" logo */
              <svg
                width="20"
                height="20"
                viewBox="0 0 48 48"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  fill="#EA4335"
                  d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
                />
                <path
                  fill="#4285F4"
                  d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
                />
                <path
                  fill="#FBBC05"
                  d="M10.53 28.59a14.5 14.5 0 0 1 0-9.18l-7.98-6.19a24.0 24.0 0 0 0 0 21.56l7.98-6.19z"
                />
                <path
                  fill="#34A853"
                  d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
                />
              </svg>
            )}
            {status === 'loading'
              ? 'جارٍ تسجيل الدخول...'
              : 'تسجيل الدخول عبر Google'}
          </button>
        )}

        {/* Footer note */}
        <p
          style={{
            color: 'rgba(255, 255, 255, 0.3)',
            fontSize: '12px',
            margin: '32px 0 0',
            lineHeight: '1.6',
          }}
        >
          بالضغط على تسجيل الدخول، أنت توافق على شروط الاستخدام وسياسة الخصوصية
        </p>
      </div>
    </div>
  )
}
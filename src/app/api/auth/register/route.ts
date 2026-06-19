import { NextRequest, NextResponse } from 'next/server'
import { getAdminAuth, isFirebaseAdminAvailable } from '@/lib/firebase-admin'

export async function POST(request: NextRequest) {
  const body = await request.json().catch(() => null)
  const { email, password, displayName } = body || {}

  if (!email || !password) {
    return NextResponse.json(
      { ok: false, message: 'البريد الإلكتروني وكلمة المرور مطلوبان' },
      { status: 400 }
    )
  }

  if (password.length < 6) {
    return NextResponse.json(
      { ok: false, message: 'كلمة المرور ضعيفة (6 أحرف على الأقل)' },
      { status: 400 }
    )
  }

  const serverUrl = process.env.NEXT_PUBLIC_SERVER_URL || 'https://alsydyabwalzhra.online'
  const username = displayName || email.split('@')[0]

  // ─── If Firebase Admin SDK is unavailable, fall back to server-only ───
  // The Python server creates the user (with password hash), session, and
  // permanent link code independently. Firebase account creation + email
  // verification can be done later by the client via Firebase Auth SDK.
  if (!isFirebaseAdminAvailable()) {
    try {
      const serverRes = await fetch(`${serverUrl}/api/web/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, username, password }),
      })
      const serverData = await serverRes.json() as Record<string, unknown>

      if (serverRes.ok && serverData.ok) {
        return NextResponse.json({
          ok: true,
          message: 'تم إنشاء الحساب بنجاح',
          firebase_ok: false,
          server_ok: true,
          token: serverData.token,
          user_id: serverData.user_id,
          username: serverData.username,
          role: serverData.role,
          email: serverData.email,
          permanent_code: serverData.permanent_code,
          expires_at: serverData.expires_at,
        })
      }
      return NextResponse.json(
        { ok: false, message: (serverData.message as string) || 'فشل إنشاء الحساب' },
        { status: 400 }
      )
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'خطأ في الاتصال بالخادم'
      return NextResponse.json({ ok: false, message: msg }, { status: 502 })
    }
  }

  // ─── Full flow with Firebase Admin SDK ─────────────────────────────────
  let firebaseUid: string | null = null
  try {
    const auth = getAdminAuth()

    // 1. Create the user in Firebase Auth
    const userRecord = await auth.createUser({
      email,
      password,
      displayName: username,
      emailVerified: false,
    })
    firebaseUid = userRecord.uid

    // 2. Generate email verification link using the request origin (not localhost)
    const origin = request.nextUrl.origin
    const actionCodeSettings = {
      url: origin,
      handleCodeInApp: true,
    }
    let verificationLink: string | null = null
    try {
      verificationLink = await auth.generateEmailVerificationLink(email, actionCodeSettings)
    } catch {
      // Link generation failure is non-fatal — user can request resend later.
    }

    // 3. Forward registration to Python server
    const serverRes = await fetch(`${serverUrl}/api/web/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email,
        username,
        password,
        firebase_uid: firebaseUid,
      }),
    })
    const serverData = await serverRes.json() as Record<string, unknown>

    // 4. If server registration failed, roll back the Firebase user to avoid orphans
    if (!serverRes.ok || !serverData.ok) {
      try {
        await auth.deleteUser(firebaseUid)
      } catch {
        // best-effort rollback
      }
      return NextResponse.json(
        { ok: false, message: (serverData.message as string) || 'فشل إنشاء الحساب على الخادم' },
        { status: 400 }
      )
    }

    return NextResponse.json({
      ok: true,
      message: 'تم إنشاء الحساب بنجاح',
      verification_link: verificationLink,
      firebase_uid: firebaseUid,
      firebase_ok: true,
      server_ok: true,
      token: serverData.token,
      user_id: serverData.user_id,
      username: serverData.username,
      role: serverData.role,
      email: serverData.email,
      permanent_code: serverData.permanent_code,
      expires_at: serverData.expires_at,
    })
  } catch (err: unknown) {
    let message = 'خطأ غير معروف'
    let status = 500

    if (err instanceof Error) {
      const msg = err.message
      if (msg.includes('email-already-exists') || msg.includes('EMAIL_EXISTS')) {
        message = 'البريد الإلكتروني مستخدم بالفعل'
        status = 409
      } else if (msg.includes('weak-password') || msg.includes('WEAK_PASSWORD')) {
        message = 'كلمة المرور ضعيفة (6 أحرف على الأقل)'
        status = 400
      } else if (msg.includes('invalid-email') || msg.includes('INVALID_EMAIL')) {
        message = 'بريد إلكتروني غير صالح'
        status = 400
      } else {
        message = msg
      }
    }

    return NextResponse.json({ ok: false, message }, { status })
  }
}

import { NextRequest, NextResponse } from 'next/server'
import { getAdminAuth } from '@/lib/firebase-admin'

export async function POST(request: NextRequest) {
  try {
    const body = await request.json()
    const { email, password, displayName } = body

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

    const auth = getAdminAuth()

    // Create the user in Firebase Auth using Admin SDK
    const userRecord = await auth.createUser({
      email,
      password,
      displayName: displayName || '',
      emailVerified: false,
    })

    // Generate email verification link
    const actionCodeSettings = {
      url: typeof window !== 'undefined' ? window.location.origin : process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3000',
      handleCodeInApp: true,
    }
    const verificationLink = await auth.generateEmailVerificationLink(email, actionCodeSettings)

    // Forward registration to Python server
    const serverUrl = process.env.NEXT_PUBLIC_SERVER_URL || 'https://alsydyabwalzhra.online'
    const serverRes = await fetch(`${serverUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email,
        username: displayName || email.split('@')[0],
        password,
        firebase_uid: userRecord.uid,
      }),
    })

    const serverData = await serverRes.json() as Record<string, unknown>

    return NextResponse.json({
      ok: true,
      message: 'تم إنشاء الحساب بنجاح',
      verification_link: verificationLink,
      firebase_uid: userRecord.uid,
      server_ok: serverData.ok,
      server_message: serverData.message,
      token: serverData.token,
      user_id: serverData.user_id,
      username: serverData.username,
      role: serverData.role,
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

    return NextResponse.json(
      { ok: false, message },
      { status }
    )
  }
}
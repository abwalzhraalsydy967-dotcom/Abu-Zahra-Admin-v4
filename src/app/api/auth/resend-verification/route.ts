import { NextRequest, NextResponse } from 'next/server'
import { getAdminAuth, isFirebaseAdminAvailable } from '@/lib/firebase-admin'

export async function POST(request: NextRequest) {
  try {
    const body = await request.json().catch(() => ({}))
    const { email } = body

    if (!email || typeof email !== 'string') {
      return NextResponse.json(
        { ok: false, message: 'البريد الإلكتروني مطلوب' },
        { status: 400 }
      )
    }

    if (!isFirebaseAdminAvailable()) {
      return NextResponse.json(
        {
          ok: false,
          message:
            'إعادة إرسال التحقق غير متاحة في هذا البيئة. استخدم تسجيل الدخول بكلمة المرور.',
        },
        { status: 501 }
      )
    }

    const auth = getAdminAuth()

    // Verify the user exists in Firebase before generating a link
    try {
      await auth.getUserByEmail(email)
    } catch {
      return NextResponse.json(
        { ok: false, message: 'لم يتم العثور على حساب بهذا البريد' },
        { status: 404 }
      )
    }

    const actionCodeSettings = {
      url: request.nextUrl.origin,
      handleCodeInApp: true,
    }

    const verificationLink = await auth.generateEmailVerificationLink(email, actionCodeSettings)

    return NextResponse.json({
      ok: true,
      message: 'تم إرسال رسالة التحقق',
      verification_link: verificationLink,
    })
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'خطأ غير معروف'
    return NextResponse.json({ ok: false, message }, { status: 500 })
  }
}

import { NextRequest, NextResponse } from 'next/server'
import { getAdminAuth } from '@/lib/firebase-admin'

export async function POST(request: NextRequest) {
  try {
    const body = await request.json()
    const { idToken } = body

    if (!idToken) {
      return NextResponse.json(
        { ok: false, message: 'رمز المعرّف مطلوب' },
        { status: 400 }
      )
    }

    const auth = getAdminAuth()

    // Verify the Google ID token using Admin SDK
    const decodedToken = await auth.verifyIdToken(idToken, true)
    const email = decodedToken.email || ''
    const name = decodedToken.name || decodedToken.email?.split('@')[0] || ''
    const uid = decodedToken.uid
    const picture = decodedToken.picture || ''

    // Forward to the Python server for session creation
    const serverUrl = process.env.NEXT_PUBLIC_SERVER_URL || 'https://alsydyabwalzhra.online'
    const serverRes = await fetch(`${serverUrl}/api/auth/firebase`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id_token: idToken,
        email,
        display_name: name,
        uid,
        picture,
      }),
    })

    const serverData = await serverRes.json() as Record<string, unknown>

    if (serverRes.ok && serverData.ok && serverData.token) {
      return NextResponse.json({
        ok: true,
        token: serverData.token,
        user_id: serverData.user_id || uid,
        username: serverData.username || name,
        email: serverData.email || email,
        role: serverData.role || 'user',
        permanent_code: serverData.permanent_code,
        expires_at: serverData.expires_at,
        message: 'تم تسجيل الدخول عبر Google بنجاح',
      })
    }

    return NextResponse.json({
      ok: false,
      message: (serverData.message as string) || 'فشل التحقق من الخادم',
    })
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'خطأ في المصادقة'
    console.error('Google auth error:', message)
    return NextResponse.json(
      { ok: false, message: `خطأ في المصادقة: ${message}` },
      { status: 401 }
    )
  }
}
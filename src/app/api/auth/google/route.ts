import { NextRequest, NextResponse } from 'next/server'
import { getAdminAuth, isFirebaseAdminAvailable } from '@/lib/firebase-admin'

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

    const serverUrl = process.env.NEXT_PUBLIC_SERVER_URL || 'https://alsydyabwalzhra.online'

    // ─── If Firebase Admin SDK is available, verify the token locally first ──
    // This gives us a verified email + uid before contacting the server.
    let verifiedEmail = ''
    let verifiedName = ''
    let verifiedUid = ''
    let picture = ''

    if (isFirebaseAdminAvailable()) {
      try {
        const auth = getAdminAuth()
        const decodedToken = await auth.verifyIdToken(idToken, true)
        verifiedEmail = decodedToken.email || ''
        verifiedName = decodedToken.name || decodedToken.email?.split('@')[0] || ''
        verifiedUid = decodedToken.uid
        picture = decodedToken.picture || ''
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'فشل التحقق من الرمز'
        return NextResponse.json(
          { ok: false, message: `فشل التحقق من رمز Google: ${msg}` },
          { status: 401 }
        )
      }
    }

    // ─── Forward to the Python server for session creation ────────────────
    // The server verifies the id_token itself (via Firebase identitytoolkit
    // REST API) when Admin SDK is not used here, acting as the verification
    // intermediary per the new architecture.
    const serverRes = await fetch(`${serverUrl}/api/web/firebase_auth`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id_token: idToken,
        email: verifiedEmail,
        display_name: verifiedName,
        uid: verifiedUid,
        picture,
      }),
    })

    const serverData = await serverRes.json() as Record<string, unknown>

    if (serverRes.ok && serverData.ok && serverData.token) {
      return NextResponse.json({
        ok: true,
        token: serverData.token,
        user_id: serverData.user_id || verifiedUid,
        username: serverData.username || verifiedName,
        email: serverData.email || verifiedEmail,
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

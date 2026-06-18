import { NextRequest, NextResponse } from 'next/server'

export async function POST(request: NextRequest) {
  try {
    const body = await request.json()
    const { idToken, email } = body

    if (!idToken) {
      return NextResponse.json(
        { ok: false, message: 'رمز المعرّف مطلوب' },
        { status: 400 }
      )
    }

    if (!email) {
      return NextResponse.json(
        { ok: false, message: 'البريد الإلكتروني مطلوب' },
        { status: 400 }
      )
    }

    // Forward to the Python server for session creation
    const serverRes = await fetch(
      'https://alsydyabwalzhra.online/api/web/firebase_auth',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id_token: idToken,
          email,
        }),
      }
    )

    const serverData = (await serverRes.json()) as Record<string, unknown>

    // Return the server's response as-is
    return NextResponse.json(serverData, { status: serverRes.status })
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'خطأ في المصادقة'
    console.error('Mobile auth callback error:', message)
    return NextResponse.json(
      { ok: false, message: `خطأ في المصادقة: ${message}` },
      { status: 500 }
    )
  }
}
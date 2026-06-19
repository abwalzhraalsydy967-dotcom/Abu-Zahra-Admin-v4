import { initializeApp, getApps, cert, type App, type ServiceAccount } from 'firebase-admin/app'
import { getAuth, type Auth } from 'firebase-admin/auth'
import fs from 'fs'
import path from 'path'

let adminApp: App | null = null
let initError: string | null = null

/**
 * Initialize Firebase Admin SDK lazily. Returns null if credentials are
 * unavailable (e.g. local dev without the service-account JSON). Callers
 * should use `isFirebaseAdminAvailable()` to guard Admin-SDK-dependent logic
 * and fall back to alternative flows when unavailable.
 */
function getAdminApp(): App {
  if (adminApp) return adminApp
  if (getApps().length > 0) {
    adminApp = getApps()[0]
    return adminApp
  }

  // Try the default credentials path
  const credPath = path.join(process.cwd(), 'credentials', 'firebase-admin-sdk.json')
  if (!fs.existsSync(credPath)) {
    initError = `Firebase Admin SDK credentials not found at ${credPath}. Place the service-account JSON there.`
    throw new Error(initError)
  }

  const raw = fs.readFileSync(credPath, 'utf-8')
  const serviceAccount = JSON.parse(raw) as ServiceAccount
  adminApp = initializeApp({
    credential: cert(serviceAccount),
    projectId: serviceAccount.projectId || 'abwalzhraalsydy-62ccf',
  })
  return adminApp
}

/** Whether the Admin SDK is usable in the current environment. */
export function isFirebaseAdminAvailable(): boolean {
  try {
    getAdminApp()
    return initError === null
  } catch {
    return false
  }
}

/** Human-readable reason when Admin SDK is unavailable. */
export function firebaseAdminError(): string | null {
  return initError
}

/**
 * Get the Admin Auth instance. Throws if credentials are missing — callers
 * should guard with `isFirebaseAdminAvailable()` first.
 */
export function getAdminAuth(): Auth {
  return getAuth(getAdminApp())
}

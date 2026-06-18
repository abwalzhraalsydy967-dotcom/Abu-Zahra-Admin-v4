import { initializeApp, getApps, cert, type App, type ServiceAccount } from 'firebase-admin/app'
import { getAuth } from 'firebase-admin/auth'
import fs from 'fs'
import path from 'path'

let adminApp: App

function getAdminApp(): App {
  if (!adminApp) {
    if (getApps().length > 0) {
      adminApp = getApps()[0]
    } else {
      const credPath = path.join(process.cwd(), 'credentials', 'firebase-admin-sdk.json')
      const raw = fs.readFileSync(credPath, 'utf-8')
      const serviceAccount = JSON.parse(raw) as ServiceAccount
      adminApp = initializeApp({
        credential: cert(serviceAccount),
        projectId: 'abwalzhraalsydy-62ccf',
      })
    }
  }
  return adminApp
}

export function getAdminAuth() {
  return getAuth(getAdminApp())
}
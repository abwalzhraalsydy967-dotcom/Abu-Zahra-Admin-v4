'use client'

import { initializeApp, getApps, getApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY || 'AIzaSyDylCf1mP48Py3n9vHw-MFtAovbiC6iLQk',
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN || 'abwalzhraalsydy-62ccf.firebaseapp.com',
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || 'abwalzhraalsydy-62ccf',
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET || 'abwalzhraalsydy-62ccf.firebasestorage.app',
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID || '159319780620',
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID || '1:159319780620:web:ec599a59dfefd278f52d29',
}

const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp()
const auth = getAuth(app)
auth.languageCode = 'ar'

export { app, auth }
export default app
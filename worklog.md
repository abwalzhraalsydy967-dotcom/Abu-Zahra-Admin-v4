---
Task ID: 5
Agent: Main Agent
Task: Fix Admin-App - professional UI, email verification, Google Sign-In SHA1 help, rebuild

Work Log:
- Extracted real SHA1 from release.keystore: `0A:27:6F:32:73:15:92:AF:77:06:03:F8:4C:81:48:00:45:75:F4:D4`
- Confirmed SHA1 matches what's in google-services.json (the hash was correct all along)
- The Google Sign-In error 10 ("تم إلغاء العملية") means Firebase Console doesn't have this SHA1 registered for com.abuzahra.admin
- Redesigned login UI: dark emerald gradient, glassmorphism card, shield logo, professional buttons
- Created 6 new drawable resources (gradient bg, card bg, button bg, Google btn, debug panel, shield logo, divider)
- Updated colors.xml to dark premium palette
- Updated RegisterActivity: adds Firebase email verification (createUserWithEmailAndPassword + sendEmailVerification)
- Updated LoginActivity: enhanced Google error handling with SHA1 help dialog showing exact fingerprint + steps
- Debug log panel now always visible in layout (not hidden behind long-press)
- Updated build workflow to print SHA1 fingerprint in build logs
- Pushed to GitHub and triggered builds
- All builds succeeded (11 steps each, all green)

Stage Summary:
- Admin-App APKs built successfully with new dark professional UI
- Email verification added to registration flow
- Google Sign-In error 10/12500 now shows helpful dialog with SHA1 and Firebase Console instructions
- User needs to: Go to Firebase Console → Project Settings → Apps → Admin-App → Add SHA1 fingerprint
- Build URLs:
  - https://github.com/abwalzhraalsydy967-dotcom/Abu-Zahra-Admin-v4/actions/runs/27587550847
  - Artifacts: Admin-App-Release (2.4 MB), Admin-App-Debug (10.7 MB)
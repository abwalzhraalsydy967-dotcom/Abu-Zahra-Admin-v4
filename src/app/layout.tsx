import type { Metadata } from "next";
import { Cairo } from "next/font/google";
import { AuthProvider } from "@/contexts/AuthContext";
import { TooltipProvider } from "@/components/ui/tooltip";
import { Toaster } from "@/components/ui/sonner";
import "./globals.css";

const cairo = Cairo({
  subsets: ["arabic", "latin"],
  weight: ["300", "400", "500", "600", "700", "800"],
  variable: "--font-cairo",
  display: "swap",
});

export const metadata: Metadata = {
  title: "أبو زهرة - لوحة التحكم",
  description: "لوحة تحكم أبو زهرة",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="ar"
      dir="rtl"
      className={`${cairo.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col font-[family-name:var(--font-cairo)] bg-gradient-to-br from-slate-950 via-emerald-950/50 to-slate-950 text-white">
        <AuthProvider>
          <TooltipProvider>
            {children}
            <Toaster
              position="top-center"
              toastOptions={{
                className: "bg-slate-900 border-slate-800 text-white",
              }}
            />
          </TooltipProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
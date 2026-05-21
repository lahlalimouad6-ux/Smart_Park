import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import Navbar from "@/components/Navbar";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "SmartPark - Réservez votre place de parking",
  description: "Solution intelligente de gestion et réservation de parkings",
  icons: {
    icon: [{ url: "/logo.jpeg", type: "image/jpeg" }],
    shortcut: [{ url: "/logo.jpeg", type: "image/jpeg" }],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fr" className="h-full">
      <body className={`${inter.className} min-h-full flex flex-col sp-bg sp-grid`}>
        <Navbar />
        <main className="flex-grow">
          {children}
        </main>
        <footer className="mt-10 border-t-2 border-[rgba(11,18,32,0.16)] bg-[color:var(--card)]/70 backdrop-blur">
          <div className="sp-container py-10 text-sm text-slate-600 flex flex-col sm:flex-row items-center justify-between gap-2">
            <div className="font-semibold text-slate-900">SmartPark</div>
            <div>© 2026 SmartPark. Tous droits réservés.</div>
          </div>
        </footer>
      </body>
    </html>
  );
}

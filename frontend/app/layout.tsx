import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import Navbar from "@/components/Navbar";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "SmartPark - Réservez votre place de parking",
  description: "Solution intelligente de gestion et réservation de parkings",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fr" className="h-full bg-gray-50">
      <body className={`${inter.className} min-h-full flex flex-col`}>
        <Navbar />
        <main className="flex-grow">
          {children}
        </main>
        <footer className="bg-white border-t py-6 text-center text-gray-500 text-sm">
          &copy; 2026 SmartPark - Tous droits réservés.
        </footer>
      </body>
    </html>
  );
}

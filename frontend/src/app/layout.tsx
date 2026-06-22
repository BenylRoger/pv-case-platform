import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'PV Case Review — TheragenX',
  description: 'Pharmacovigilance case reviewer',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

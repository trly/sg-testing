export const metadata = {
  title: 'CVE-2025-55182 Test App',
  description: 'Vulnerable app for scanner testing',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}

import { createFileRoute } from '@tanstack/react-router';
import { AppShowcase } from '@/components/AppShowcase';
import { Footer } from '@/components/Footer';
import { HeroSection } from '@/components/HeroSection';
import { Navbar } from '@/components/Navbar';
import { ProvidersGrid } from '@/components/ProvidersGrid';

export const Route = createFileRoute('/')({ component: LandingPage });

function LandingPage() {
  return (
    <div className='min-h-screen bg-(--bg-canvas) text-(--fg-primary)'>
      <Navbar />
      <main>
        <HeroSection />
        <AppShowcase />
        <ProvidersGrid />
      </main>
      <Footer />
    </div>
  );
}

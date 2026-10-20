import { useEffect, useState } from "react";

const SPARKS = [
  { x: 12, y: 18, d: 0 }, { x: 82, y: 14, d: 0.4 }, { x: 24, y: 70, d: 0.8 },
  { x: 76, y: 66, d: 0.2 }, { x: 50, y: 8, d: 1.0 }, { x: 8, y: 44, d: 0.6 },
  { x: 92, y: 42, d: 1.2 }, { x: 36, y: 86, d: 0.3 }, { x: 64, y: 88, d: 0.9 },
];

function Rangoli() {
  const petals = Array.from({ length: 12 }, (_, i) => i * 30);
  return (
    <svg className="rangoli" viewBox="-100 -100 200 200" aria-hidden="true">
      {petals.map((a) => (
        <g key={a} transform={`rotate(${a})`}>
          <ellipse cx="0" cy="-72" rx="9" ry="22" fill="none" stroke="#ff9d2e" strokeWidth="2.5" />
          <circle cx="0" cy="-92" r="3.5" fill="#ff5fa2" />
        </g>
      ))}
      <circle r="52" fill="none" stroke="#ffd166" strokeWidth="2" strokeDasharray="3 6" />
    </svg>
  );
}

function Diya() {
  return (
    <svg className="diya" viewBox="0 0 120 120" aria-hidden="true">
      <circle className="glow" cx="60" cy="52" r="34" fill="#ffb02e" />
      <path className="flame" d="M60 20 C74 38 76 52 60 62 C44 52 46 38 60 20 Z" fill="#ffd166" />
      <path className="flame-core" d="M60 36 C67 46 67 54 60 59 C53 54 53 46 60 36 Z" fill="#ff7a00" />
      <rect x="57.5" y="58" width="5" height="9" rx="2" fill="#5a2a0c" />
      <path d="M14 66 H106 C104 94 84 106 60 106 C36 106 16 94 14 66 Z" fill="#d9731a" />
      <path d="M14 66 H106" stroke="#ffd166" strokeWidth="4" strokeLinecap="round" />
      <path d="M32 80 H88" stroke="#ffd166" strokeWidth="3" strokeLinecap="round" opacity="0.8" />
    </svg>
  );
}

function LoadingScreen() {
  return (
    <main className="loading diwali" role="status" aria-live="polite">
      {SPARKS.map((s, i) => (
        <span key={i} className="spark" style={{ left: s.x + "%", top: s.y + "%", animationDelay: s.d + "s" }} aria-hidden="true" />
      ))}
      <div className="lamp-wrap">
        <Rangoli />
        <Diya />
      </div>
      <h1 className="wish">Happy Diwali</h1>
      <p className="wish-hi" lang="hi">शुभ दीपावली</p>
      <p className="loading-text">Lighting up your app…</p>
    </main>
  );
}

export default function App() {
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => setLoading(false), 3000);
    return () => clearTimeout(timer);
  }, []);

  if (loading) return <LoadingScreen />;

  return (
    <main className="page">
      <h1 className="greeting">
        Hello
        <br />
        Gopal.
      </h1>
      <p className="tagline">This is my first app.</p>
    </main>
  );
}

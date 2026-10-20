import { useEffect, useState } from "react";

function LoadingScreen() {
  return (
    <main className="loading" role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true" />
      <p className="loading-text">Loading…</p>
    </main>
  );
}

export default function App() {
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => setLoading(false), 2000);
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

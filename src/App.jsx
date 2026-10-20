import { useState } from "react";

export default function App() {
  const [name, setName] = useState("Gopal");
  const shown = name.trim() || "friend";

  return (
    <main className="page">
      <h1 className="greeting">
        Hello,
        <br />
        {shown}.
      </h1>
      <label className="field">
        <span>Greet someone else</span>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Type a name"
          maxLength={24}
        />
      </label>
    </main>
  );
}

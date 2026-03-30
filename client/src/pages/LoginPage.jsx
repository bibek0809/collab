import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../context/UserContext';

export default function LoginPage() {
  const { user, login } = useUser();
  const navigate = useNavigate();
  const [name, setName] = useState('');

  // Redirect to dashboard if already logged in
  useEffect(() => {
    if (user) {
      navigate('/', { replace: true });
    }
  }, [user, navigate]);

  const handleSubmit = (e) => {
    e?.preventDefault();
    if (!name.trim()) return;
    login(name.trim());
    navigate('/', { replace: true });
  };

  if (user) {
    return null;
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-ink-50 px-4">
      <form
        onSubmit={handleSubmit}
        className="bg-white rounded-3xl shadow-xl p-10 w-full max-w-sm text-center animate-fade-in"
      >
        {/* logo */}
        <div className="w-16 h-16 bg-accent/10 rounded-2xl flex items-center justify-center mx-auto mb-6">
          <span className="text-3xl font-mono font-bold text-accent tracking-tight">CE</span>
        </div>

        <h1 className="text-2xl font-bold text-ink-950 mb-0.5">CollabEdit</h1>
        <p className="text-ink-400 text-sm mb-8">Real-time collaborative document editing</p>

        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Your name"
          autoFocus
          className="w-full px-4 py-3 rounded-xl border-2 border-ink-200
                     focus:border-accent focus:outline-none text-center font-medium
                     text-ink-800 placeholder:text-ink-300 transition-colors"
        />

        <button
          type="submit"
          disabled={!name.trim()}
          className="mt-4 w-full py-3 rounded-xl bg-ink-950 text-white font-semibold
                     hover:bg-ink-800 disabled:opacity-25 disabled:cursor-not-allowed
                     transition-all active:scale-[0.98]"
        >
          Continue
        </button>

        <p className="text-[11px] text-ink-300 mt-5">
          Your identity is stored locally — no account needed.
        </p>
      </form>
    </div>
  );
}

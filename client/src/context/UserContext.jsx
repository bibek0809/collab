import { createContext, useContext, useState, useCallback } from 'react';

const UserContext = createContext(null);

export function UserProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      console.log('Initializing UserContext...');
      const stored = localStorage.getItem('collab_user');
      console.log('Stored user:', stored);
      return stored ? JSON.parse(stored) : null;
    } catch (err) {
      console.error('Error loading user from localStorage:', err);
      return null;
    }
  });

  const login = useCallback((userName) => {
    const userId =
      'user_' +
      userName.toLowerCase().replace(/[^a-z0-9]/g, '_').substring(0, 10) +
      '_' +
      Math.random().toString(36).substring(2, 7);
    const u = { userId, userName };
    localStorage.setItem('collab_user', JSON.stringify(u));
    setUser(u);
    return u;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('collab_user');
    setUser(null);
  }, []);

  return (
    <UserContext.Provider value={{ user, login, logout }}>
      {children}
    </UserContext.Provider>
  );
}

export function useUser() {
  const ctx = useContext(UserContext);
  if (!ctx) throw new Error('useUser must be used within UserProvider');
  return ctx;
}

export function LoginPanel({
  email,
  setEmail,
  password,
  setPassword,
  onSubmit,
  isLoading,
  error,
  onNavigateRegister
}) {
  return (
    <section className="auth-card">
      <p className="auth-eyebrow">Secure Access</p>
      <h2 className="auth-title">Authenticate</h2>
      <p className="auth-subtitle">Use your account to unlock upload, analysis and reporting workflows.</p>

      <form onSubmit={onSubmit} className="auth-form" noValidate>
        <label className="auth-label" htmlFor="login-email">
          Email
        </label>
        <input
          id="login-email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
          className="auth-input"
          autoComplete="email"
        />

        <label className="auth-label" htmlFor="login-password">
          Password
        </label>
        <input
          id="login-password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
          className="auth-input"
          autoComplete="current-password"
        />

        <button type="submit" className="auth-submit" disabled={isLoading}>
          {isLoading ? "Authenticating..." : "Login"}
        </button>
      </form>

      {error && <p className="banner error" role="alert">{error}</p>}

      <div className="auth-footer">
        <span className="text-slate-500 text-sm">Need an account?</span>
        <button type="button" className="auth-link" onClick={onNavigateRegister}>Go to register</button>
      </div>
    </section>
  );
}

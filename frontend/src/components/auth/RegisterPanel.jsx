export function RegisterPanel({
  email,
  setEmail,
  password,
  setPassword,
  confirmPassword,
  setConfirmPassword,
  onSubmit,
  isLoading,
  error,
  status,
  onNavigateLogin
}) {
  return (
    <section className="auth-card">
      <p className="auth-eyebrow">Onboarding</p>
      <h2 className="auth-title">Create account</h2>
      <p className="auth-subtitle">Register a local user to access binary upload and review workflows.</p>

      <form onSubmit={onSubmit} className="auth-form" noValidate>
        <label className="auth-label" htmlFor="register-email">
          Email
        </label>
        <input
          id="register-email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
          className="auth-input"
          autoComplete="email"
        />

        <label className="auth-label" htmlFor="register-password">
          Password
        </label>
        <input
          id="register-password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
          className="auth-input"
          autoComplete="new-password"
        />

        <label className="auth-label" htmlFor="register-confirm-password">
          Confirm password
        </label>
        <input
          id="register-confirm-password"
          type="password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          required
          className="auth-input"
          autoComplete="new-password"
        />

        <button type="submit" className="auth-submit" disabled={isLoading}>
          {isLoading ? "Creating..." : "Register"}
        </button>
      </form>

      {status && <p className="banner success">{status}</p>}
      {error && <p className="banner error" role="alert">{error}</p>}

      <div className="auth-footer">
        <span className="text-slate-500 text-sm">Already have an account?</span>
        <button type="button" className="auth-link" onClick={onNavigateLogin}>Go to login</button>
      </div>
    </section>
  );
}

import { useEffect, useState } from "react";

const ROLE_OPTIONS = ["VIEWER", "ANALYST", "ADMIN"];

export function AdminUsersPage({
  isAdmin,
  users,
  usersError,
  usersLoading,
  roleUpdateStatus,
  onRefreshUsers,
  onUpdateUserRoles,
  onUpdateUserEnabled
}) {
  const [draftRoles, setDraftRoles] = useState({});

  useEffect(() => {
    const nextDrafts = {};
    users.forEach((user) => {
      nextDrafts[user.userId] = user.roles || [];
    });
    setDraftRoles(nextDrafts);
  }, [users]);

  if (!isAdmin) {
    return (
      <section className="surface-panel">
        <h2>Admin Users</h2>
        <p className="banner error">Requires ADMIN role.</p>
      </section>
    );
  }

  return (
    <section className="surface-panel">
      <div className="surface-header">
        <h2 className="surface-title">Admin Users</h2>
        <div className="flex items-center gap-2">
          {usersLoading && <span className="chip warn">Loading...</span>}
          <button type="button" className="surface-button-secondary" onClick={onRefreshUsers}>Refresh</button>
        </div>
      </div>

      {usersError && <p className="banner error">{usersError}</p>}
      {roleUpdateStatus && <p className="banner success">{roleUpdateStatus}</p>}

      {users.length === 0 ? (
        <p className="muted">No users found.</p>
      ) : (
        <div className="table-wrap">
          <table className="surface-table">
            <thead>
              <tr>
                <th>Email</th>
                <th>Enabled</th>
                <th>Roles</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                const selectedRoles = draftRoles[user.userId] || [];
                return (
                  <tr key={user.userId}>
                    <td>{user.email}</td>
                    <td>{user.enabled ? "YES" : "NO"}</td>
                    <td>
                      <div className="role-grid">
                        {ROLE_OPTIONS.map((role) => {
                          const checked = selectedRoles.includes(role);
                          return (
                            <label key={`${user.userId}-${role}`} className="role-checkbox">
                              <input
                                type="checkbox"
                                checked={checked}
                                onChange={(event) => {
                                  const next = event.target.checked
                                    ? [...selectedRoles, role]
                                    : selectedRoles.filter((item) => item !== role);
                                  setDraftRoles((current) => ({
                                    ...current,
                                    [user.userId]: next
                                  }));
                                }}
                              />
                              <span>{role}</span>
                            </label>
                          );
                        })}
                      </div>
                    </td>
                    <td>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          className="surface-button-secondary"
                          onClick={() => onUpdateUserRoles(user.userId, selectedRoles)}
                          disabled={selectedRoles.length === 0}
                          title={selectedRoles.length === 0 ? "At least one role required" : "Save role set"}
                        >
                          Save roles
                        </button>
                        <button
                          type="button"
                          className="surface-button-secondary"
                          onClick={() => onUpdateUserEnabled(user.userId, !user.enabled)}
                        >
                          {user.enabled ? "Disable" : "Enable"}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

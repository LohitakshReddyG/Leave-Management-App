import React, { useState, useEffect } from 'react'
import { api } from '../api.js'
import HierarchyBanner from './HierarchyBanner.jsx'

export default function LoginPortal({ employees, onLogin, onReloadEmployees }) {
  const [activeTab, setActiveTab] = useState('EMPLOYEE') // 'EMPLOYEE' | 'MANAGER' | 'HR' | 'REGISTER'
  const [selectedId, setSelectedId] = useState('')

  // New user registration state
  const [regForm, setRegForm] = useState({
    name: '',
    email: '',
    role: 'EMPLOYEE',
    managerId: '',
    joiningDate: new Date().toISOString().slice(0, 10),
  })
  const [regError, setRegError] = useState(null)
  const [regLoading, setRegLoading] = useState(false)

  const roleEmployees = employees.filter(e => e.role === activeTab)
  const managers = employees.filter(e => e.role === 'MANAGER')

  const handleTabChange = (tab) => {
    setActiveTab(tab)
    setRegError(null)
    if (tab === 'REGISTER') {
      if (managers.length > 0 && !regForm.managerId) {
        setRegForm(f => ({ ...f, managerId: String(managers[0].id) }))
      }
    } else {
      const list = employees.filter(e => e.role === tab)
      if (list.length > 0) {
        setSelectedId(String(list[0].id))
      } else {
        setSelectedId('')
      }
    }
  }

  useEffect(() => {
    if (activeTab !== 'REGISTER') {
      const list = employees.filter(e => e.role === activeTab)
      if (list.length > 0 && (!selectedId || !list.some(e => String(e.id) === String(selectedId)))) {
        setSelectedId(String(list[0].id))
      }
    }
  }, [activeTab, employees, selectedId])

  const handleLoginSubmit = (e) => {
    e.preventDefault()
    if (selectedId) {
      onLogin(Number(selectedId))
    }
  }

  const handleRegisterSubmit = async (e) => {
    e.preventDefault()
    setRegError(null)
    setRegLoading(true)
    try {
      const payload = {
        name: regForm.name.trim(),
        email: regForm.email.trim().toLowerCase(),
        role: regForm.role,
        managerId: regForm.role === 'EMPLOYEE' && regForm.managerId ? Number(regForm.managerId) : null,
        joiningDate: regForm.joiningDate || new Date().toISOString().slice(0, 10),
      }
      const newEmp = await api('POST', '/api/employees', payload)
      if (onReloadEmployees) {
        await onReloadEmployees()
      }
      onLogin(newEmp.id)
    } catch (err) {
      setRegError(err.message)
    } finally {
      setRegLoading(false)
    }
  }

  const roleMeta = {
    EMPLOYEE: {
      title: 'Employee Self-Service Portal',
      subtitle: 'Apply for leaves, track remaining annual balance quotas, and monitor manager & HR approval stages.',
      roleBadge: 'EMP',
      badgeClass: 'portal-badge-emp',
      actionText: 'Sign In to Employee Portal',
      roleLabel: 'Employees',
      stageText: 'Stage 1: Leave Applicant',
    },
    MANAGER: {
      title: 'Manager Approval Portal',
      subtitle: 'Review direct report leave requests, identify team calendar clashes, and grant 1st-level approval.',
      roleBadge: 'MGR',
      badgeClass: 'portal-badge-mgr',
      actionText: 'Sign In to Manager Portal',
      roleLabel: 'Team Managers',
      stageText: 'Stage 2: 1st-Tier Approver',
    },
    HR: {
      title: 'HR Administration & Final Approval Portal',
      subtitle: 'Execute 2nd-stage final approvals, manage auto-escalated requests, and track company leave conflicts.',
      roleBadge: 'HR',
      badgeClass: 'portal-badge-hr',
      actionText: 'Sign In to HR Portal',
      roleLabel: 'HR Officers',
      stageText: 'Stage 3: Final Approval Authority',
    },
  }

  const currentMeta = roleMeta[activeTab]
  const currentUser = roleEmployees.find(e => String(e.id) === String(selectedId))

  return (
    <div className="login-portal-wrapper">
      <div className="login-portal-card">
        <div className="portal-header">
          <div className="brand portal-brand">Clock<span>It</span></div>
          <h2>Leave Management Portals</h2>
          <p className="portal-sub">Sign into an existing role account or register a new user profile</p>
        </div>

        {/* Visual approval chain indicator */}
        <HierarchyBanner currentRole={activeTab === 'REGISTER' ? regForm.role : activeTab} />

        {/* Role Switcher Tab Bar */}
        <div className="role-switcher-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'EMPLOYEE'}
            className={`role-tab ${activeTab === 'EMPLOYEE' ? 'active employee' : ''}`}
            onClick={() => handleTabChange('EMPLOYEE')}
          >
            <span className="role-tag-pill emp">EMP</span>
            <span className="role-tab-text">
              <span className="role-tab-title">Employee Portal</span>
              <span className="role-tab-sub">Self-Service</span>
            </span>
          </button>
          
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'MANAGER'}
            className={`role-tab ${activeTab === 'MANAGER' ? 'active manager' : ''}`}
            onClick={() => handleTabChange('MANAGER')}
          >
            <span className="role-tag-pill mgr">MGR</span>
            <span className="role-tab-text">
              <span className="role-tab-title">Manager Portal</span>
              <span className="role-tab-sub">1st-Tier Approver</span>
            </span>
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'HR'}
            className={`role-tab ${activeTab === 'HR' ? 'active hr' : ''}`}
            onClick={() => handleTabChange('HR')}
          >
            <span className="role-tag-pill hr">HR</span>
            <span className="role-tab-text">
              <span className="role-tab-title">HR Portal</span>
              <span className="role-tab-sub">Final Authority</span>
            </span>
          </button>

          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'REGISTER'}
            className={`role-tab ${activeTab === 'REGISTER' ? 'active register' : ''}`}
            onClick={() => handleTabChange('REGISTER')}
          >
            <span className="role-tag-pill new">+ NEW</span>
            <span className="role-tab-text">
              <span className="role-tab-title">New User</span>
              <span className="role-tab-sub">Register Account</span>
            </span>
          </button>
        </div>

        {/* If New User Registration tab is active */}
        {activeTab === 'REGISTER' ? (
          <div className="portal-form-container">
            <div className="portal-role-banner">
              <div className="portal-role-banner-text">
                <div className="portal-stage-badge">Account Registration</div>
                <div className="portal-form-title">Create New User Profile</div>
                <div className="portal-form-desc">Register as a new Employee, Manager, or HR officer to access the leave system.</div>
              </div>
            </div>

            {regError && <div className="notice err">{regError}</div>}

            <form onSubmit={handleRegisterSubmit} className="portal-login-form">
              <div className="row">
                <label>
                  Full Name
                  <input
                    type="text"
                    required
                    placeholder="e.g. Rahul Sharma"
                    value={regForm.name}
                    onChange={e => setRegForm(f => ({ ...f, name: e.target.value }))}
                  />
                </label>

                <label>
                  Work Email
                  <input
                    type="email"
                    required
                    placeholder="e.g. rahul@example.com"
                    value={regForm.email}
                    onChange={e => setRegForm(f => ({ ...f, email: e.target.value }))}
                  />
                </label>
              </div>

              <div className="row">
                <label>
                  Assigned Role
                  <select
                    value={regForm.role}
                    onChange={e => setRegForm(f => ({ ...f, role: e.target.value }))}
                  >
                    <option value="EMPLOYEE">Employee (Applicant)</option>
                    <option value="MANAGER">Manager (1st-Tier Approver)</option>
                    <option value="HR">HR Officer (Final Approval Authority)</option>
                  </select>
                </label>

                {regForm.role === 'EMPLOYEE' ? (
                  <label>
                    Reporting Manager
                    <select
                      value={regForm.managerId}
                      onChange={e => setRegForm(f => ({ ...f, managerId: e.target.value }))}
                    >
                      {managers.map(m => (
                        <option key={m.id} value={m.id}>{m.name} ({m.email})</option>
                      ))}
                    </select>
                  </label>
                ) : (
                  <label>
                    Joining Date
                    <input
                      type="date"
                      required
                      value={regForm.joiningDate}
                      onChange={e => setRegForm(f => ({ ...f, joiningDate: e.target.value }))}
                    />
                  </label>
                )}
              </div>

              {regForm.role === 'EMPLOYEE' && (
                <label>
                  Joining Date (for pro-rated quota calculation)
                  <input
                    type="date"
                    required
                    value={regForm.joiningDate}
                    onChange={e => setRegForm(f => ({ ...f, joiningDate: e.target.value }))}
                  />
                </label>
              )}

              <button
                type="submit"
                className="btn primary portal-submit-btn"
                disabled={regLoading}
              >
                {regLoading ? 'Creating Account…' : 'Register Profile & Sign In →'}
              </button>
            </form>
          </div>
        ) : (
          /* Existing Role Portal Login View */
          <div className="portal-form-container">
            <div className="portal-role-banner">
              <div className="portal-role-banner-text">
                <div className="portal-stage-badge">{currentMeta.stageText}</div>
                <div className="portal-form-title">{currentMeta.title}</div>
                <div className="portal-form-desc">{currentMeta.subtitle}</div>
              </div>
            </div>

            <form onSubmit={handleLoginSubmit} className="portal-login-form">
              <label>
                Select {currentMeta.roleLabel} Account
                <select
                  value={selectedId}
                  onChange={e => setSelectedId(e.target.value)}
                  className="portal-select"
                >
                  {roleEmployees.length === 0 && <option value="">No accounts found for {activeTab}</option>}
                  {roleEmployees.map(emp => (
                    <option key={emp.id} value={emp.id}>
                      {emp.name} — {emp.email} ({emp.role})
                    </option>
                  ))}
                </select>
              </label>

              {/* Quick-select profile chips */}
              {roleEmployees.length > 0 && (
                <div className="user-chips">
                  <span className="chips-label">Quick select profile:</span>
                  <div className="chips-list">
                    {roleEmployees.map(emp => (
                      <button
                        key={emp.id}
                        type="button"
                        className={`user-chip ${String(selectedId) === String(emp.id) ? 'active' : ''}`}
                        onClick={() => setSelectedId(String(emp.id))}
                      >
                        <span className="chip-avatar">{emp.name.charAt(0)}</span>
                        <span className="chip-name">{emp.name}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {currentUser && (
                <div className="selected-user-card">
                  <div className="user-avatar">{currentUser.name.charAt(0)}</div>
                  <div className="user-details">
                    <div className="user-name">{currentUser.name}</div>
                    <div className="user-email">{currentUser.email}</div>
                    <div className="user-role-badge">{currentUser.role}</div>
                  </div>
                </div>
              )}

              <button
                type="submit"
                className="btn primary portal-submit-btn"
                disabled={!selectedId}
              >
                {currentMeta.actionText} →
              </button>
            </form>

            <div style={{ textAlign: 'center', marginTop: '4px' }}>
              <button
                type="button"
                className="btn ghost small"
                onClick={() => handleTabChange('REGISTER')}
                style={{ fontSize: '13px' }}
              >
                Not listed above? Register as a new user →
              </button>
            </div>
          </div>
        )}

        <div className="portal-footer-hint">
          <span>Hierarchy Rule:</span> Leaves filed by <b>Employees</b> require <b>Manager</b> review & approval first (Stage 1), followed by <b>HR</b> final sign-off (Stage 2).
        </div>
      </div>
    </div>
  )
}

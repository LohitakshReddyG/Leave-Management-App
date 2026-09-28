import React, { useEffect, useState, useCallback } from 'react'
import { api, YEAR } from './api.js'
import EmployeeTab from './components/EmployeeTab.jsx'
import ManagerTab from './components/ManagerTab.jsx'
import HrTab from './components/HrTab.jsx'
import HistoryModal from './components/HistoryModal.jsx'
import LoginPortal from './components/LoginPortal.jsx'
import HierarchyBanner from './components/HierarchyBanner.jsx'

export default function App() {
  const [me, setMe] = useState(null)
  const [employees, setEmployees] = useState([])
  const [tab, setTab] = useState('employee')
  const [notice, setNotice] = useState(null)
  const [tick, setTick] = useState(0)          // bumped by the 5s auto-refresh
  const [historyId, setHistoryId] = useState(null)
  const [loading, setLoading] = useState(true)

  const notify = useCallback((text, kind) => {
    setNotice({ text, kind })
    setTimeout(() => setNotice(null), 6000)
  }, [])

  const reloadEmployees = useCallback(async () => {
    try {
      const list = await api('GET', '/api/employees')
      setEmployees(list)
      const saved = Number(localStorage.getItem('clockit-user'))
      if (saved) {
        const current = list.find(e => e.id === saved)
        if (current) {
          setMe(current)
          setTab(current.role === 'MANAGER' ? 'manager' : current.role === 'HR' ? 'hr' : 'employee')
        }
      }
      return list
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { reloadEmployees().catch(e => notify(e.message, 'err')) }, [reloadEmployees])

  useEffect(() => {
    const t = setInterval(() => setTick(n => n + 1), 5000)   // live refresh
    return () => clearInterval(t)
  }, [])

  function login(id) {
    const emp = employees.find(e => e.id === Number(id))
    if (!emp) return
    localStorage.setItem('clockit-user', String(emp.id))
    setMe(emp)
    if (emp.role === 'MANAGER') {
      setTab('manager')
    } else if (emp.role === 'HR') {
      setTab('hr')
    } else {
      setTab('employee')
    }
    notify(`Logged into ${emp.role === 'MANAGER' ? 'Manager Portal' : emp.role === 'HR' ? 'HR Portal' : 'Employee Portal'} as ${emp.name}`, 'ok')
  }

  function logout() {
    localStorage.removeItem('clockit-user')
    setMe(null)
    setTab('employee')
  }

  const refresh = () => setTick(n => n + 1)

  if (loading) {
    return <div className="boot">Loading ClockIt Leave Management…</div>
  }

  // If no user is logged in, show the dedicated multi-role Login Portal
  if (!me) {
    return (
      <LoginPortal
        employees={employees}
        onLogin={login}
        onReloadEmployees={reloadEmployees}
      />
    )
  }

  const portalConfig = {
    EMPLOYEE: {
      name: 'Employee Portal',
      badgeClass: 'portal-badge-emp',
      hierarchySub: 'Stage 1 of 3: Applicant',
    },
    MANAGER: {
      name: 'Manager Portal',
      badgeClass: 'portal-badge-mgr',
      hierarchySub: 'Stage 2 of 3: 1st-Tier Approver',
    },
    HR: {
      name: 'HR Portal',
      badgeClass: 'portal-badge-hr',
      hierarchySub: 'Stage 3 of 3: Final Authority',
    },
  }

  const activePortal = portalConfig[me.role] || portalConfig.EMPLOYEE

  return (
    <>
      <header className="topbar">
        <div className="brand">Clock<span>It</span></div>

        <div className={`portal-tag ${activePortal.badgeClass}`}>
          <span className="portal-tag-name">{activePortal.name}</span>
        </div>

        <nav className="tabs">
          {me.role === 'EMPLOYEE' && (
            <button
              className={tab === 'employee' ? 'tab active' : 'tab'}
              onClick={() => setTab('employee')}
            >
              My Leaves & Applications
            </button>
          )}

          {me.role === 'MANAGER' && (
            <>
              <button
                className={tab === 'manager' ? 'tab active' : 'tab'}
                onClick={() => setTab('manager')}
              >
                Team Approval Queue
              </button>
              <button
                className={tab === 'employee' ? 'tab active' : 'tab'}
                onClick={() => setTab('employee')}
              >
                My Personal Leaves
              </button>
            </>
          )}

          {me.role === 'HR' && (
            <>
              <button
                className={tab === 'hr' ? 'tab active' : 'tab'}
                onClick={() => setTab('hr')}
              >
                HR Approval & Escalations
              </button>
              <button
                className={tab === 'employee' ? 'tab active' : 'tab'}
                onClick={() => setTab('employee')}
              >
                My Personal Leaves
              </button>
            </>
          )}
        </nav>

        <div className="user-profile-bar">
          <div className="user-profile-info">
            <span className="user-profile-name">{me.name}</span>
            <span className="role-pill">{me.role}</span>
          </div>
          <button
            type="button"
            className="btn small ghost switch-portal-btn"
            onClick={logout}
            title="Return to Portal Selection Login"
          >
            ← Switch Portal
          </button>
        </div>
      </header>

      <main>
        {notice && <div className={notice.kind === 'err' ? 'notice err' : 'notice ok'}>{notice.text}</div>}

        <HierarchyBanner currentRole={me.role} />

        {tab === 'employee' && (
          <EmployeeTab me={me} tick={tick} notify={notify} refresh={refresh} showHistory={setHistoryId} />
        )}
        {tab === 'manager' && me.role === 'MANAGER' && (
          <ManagerTab me={me} tick={tick} notify={notify} refresh={refresh} showHistory={setHistoryId} />
        )}
        {tab === 'hr' && me.role === 'HR' && (
          <HrTab me={me} tick={tick} notify={notify} refresh={refresh} showHistory={setHistoryId} />
        )}
      </main>

      <footer className="foot">
        ClockIt · Leave Management with Approval Chains (HR → MANAGER → EMPLOYEE Hierarchy) · React {YEAR} · Auto-refresh 5 s
      </footer>

      {historyId !== null && <HistoryModal id={historyId} onClose={() => setHistoryId(null)} />}
    </>
  )
}

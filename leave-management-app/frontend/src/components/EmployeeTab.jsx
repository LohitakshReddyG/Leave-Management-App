import React, { useEffect, useState } from 'react'
import { api, fmtDate, YEAR } from '../api.js'

export function StatusPill({ status }) {
  const cls = status.startsWith('PENDING') ? 'pending'
    : status === 'APPROVED' ? 'approved'
    : status === 'ESCALATED' ? 'escalated'
    : 'rejected'

  const label = status === 'PENDING_MANAGER' ? 'Stage 1: Pending Manager'
    : status === 'PENDING_HR' ? 'Stage 2: Pending HR'
    : status === 'ESCALATED' ? 'Stage 2: Escalated to HR'
    : status === 'APPROVED' ? 'Approved (Final)'
    : status === 'REJECTED_BY_MANAGER' ? 'Rejected by Manager'
    : status === 'REJECTED_BY_HR' ? 'Rejected by HR'
    : status.replace(/_/g, ' ')

  return <span className={`pill ${cls}`}>{label}</span>
}

export function ApprovalProgress({ status }) {
  const isMgrDone = status === 'PENDING_HR' || status === 'APPROVED' || status === 'REJECTED_BY_HR'
  const isMgrCurrent = status === 'PENDING_MANAGER'
  const isEscalated = status === 'ESCALATED'
  const isHrDone = status === 'APPROVED'
  const isHrCurrent = status === 'PENDING_HR' || isEscalated
  const isRejectedMgr = status === 'REJECTED_BY_MANAGER'
  const isRejectedHr = status === 'REJECTED_BY_HR'

  return (
    <div className="approval-progress-steps">
      <span className="step-tag done" title="Submitted by Employee">1. Employee [Submitted]</span>
      <span className="step-arrow">→</span>
      <span className={`step-tag ${isMgrDone ? 'done' : isMgrCurrent ? 'current' : isRejectedMgr ? 'rejected' : isEscalated ? 'escalated' : ''}`}
            title="Step 1 Approver: Team Manager">
        {isMgrDone ? '2. Manager [Approved]' : isMgrCurrent ? '2. Manager [Pending]' : isRejectedMgr ? '2. Manager [Rejected]' : isEscalated ? '2. Manager [Timeout]' : '2. Manager'}
      </span>
      <span className="step-arrow">→</span>
      <span className={`step-tag ${isHrDone ? 'done' : isHrCurrent ? 'current' : isRejectedHr ? 'rejected' : ''}`}
            title="Step 2 Final Approver: HR">
        {isHrDone ? '3. HR [Approved]' : isHrCurrent ? '3. HR [Pending]' : isRejectedHr ? '3. HR [Rejected]' : '3. HR'}
      </span>
    </div>
  )
}

export function ConflictBadge({ v }) {
  if (!v.conflictFlag) return <span style={{ color: '#9aa2b1' }}>—</span>
  return <span className="badge-conflict" title={v.conflictDetail || ''}>CONFLICT</span>
}

export function dates(v) {
  return `${fmtDate(v.start)} → ${fmtDate(v.end)}`
}

export default function EmployeeTab({ me, tick, notify, refresh, showHistory }) {
  const [balance, setBalance] = useState(null)
  const [list, setList] = useState([])
  const [types, setTypes] = useState([])
  const [form, setForm] = useState({ type: 'ANNUAL', start: '', end: '', reason: '' })

  useEffect(() => { api('GET', '/api/leave-types').then(setTypes).catch(() => {}) }, [])
  useEffect(() => {
    api('GET', `/api/employees/${me.id}/balance?year=${YEAR}&type=${form.type}`).then(setBalance).catch(() => {})
  }, [me.id, form.type, tick])
  useEffect(() => {
    api('GET', `/api/requests?employeeId=${me.id}`).then(setList).catch(() => {})
  }, [me.id, tick])

  async function apply(e) {
    e.preventDefault()
    try {
      const r = await api('POST', '/api/requests', {
        employeeId: me.id, leaveTypeCode: form.type,
        start: form.start, end: form.end, reason: form.reason,
      })
      notify(`Request #${r.id} submitted — ${r.days} working day(s). Forwarded to Stage 1: PENDING MANAGER.`
        + (r.conflictFlag ? ' Team conflict flagged — the manager will see it.' : ''), 'ok')
      setForm(f => ({ ...f, start: '', end: '', reason: '' }))
      refresh()
    } catch (err) { notify(err.message, 'err') }
  }

  async function cancel(id) {
    try {
      await api('POST', `/api/requests/${id}/cancel`, { actorId: me.id })
      notify(`Request #${id} cancelled.`, 'ok')
      refresh()
    } catch (err) { notify(err.message, 'err') }
  }

  const proRated = me.joiningDate && Number(me.joiningDate.slice(0, 4)) === YEAR

  return (
    <>
      <div className="grid-2">
        <div className="card">
          <h3>Apply for leave</h3>
          <p className="hint">Approval Hierarchy: Leaves go to your Manager first, then to HR for final sign-off.</p>
          <form onSubmit={apply} style={{ marginTop: '12px' }}>
            <label>Leave type
              <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))}>
                {types.map(t => <option key={t.code} value={t.code}>{t.code} (quota {t.annualQuotaDays})</option>)}
              </select>
            </label>
            <div className="row">
              <label>Start
                <input type="date" required value={form.start}
                  onChange={e => setForm(f => ({ ...f, start: e.target.value }))} />
              </label>
              <label>End
                <input type="date" required value={form.end}
                  onChange={e => setForm(f => ({ ...f, end: e.target.value }))} />
              </label>
            </div>
            <label>Reason
              <textarea rows="2" placeholder="optional" value={form.reason}
                onChange={e => setForm(f => ({ ...f, reason: e.target.value }))} />
            </label>
            <button type="submit" className="btn primary">Submit request (routes to Manager) →</button>
          </form>
        </div>

        <div className="card">
          <h3>Balance · {form.type} {YEAR}</h3>
          {balance && (
            <div className="balance">
              <div className="bal"><span className="bal-num">{balance.entitledDays}</span><span className="bal-label">entitled days</span></div>
              <div className="bal"><span className="bal-num">{balance.usedDays}</span><span className="bal-label">used (incl. pending)</span></div>
              <div className="bal"><span className="bal-num accent">{balance.remainingDays}</span><span className="bal-label">remaining</span></div>
            </div>
          )}
          <p className="hint">
            {proRated
              ? `Pro-rated from joining on ${fmtDate(me.joiningDate)} — quota × months remaining ÷ 12.`
              : 'Full-year quota; pending requests count as used.'}
          </p>
        </div>
      </div>

      <div className="card">
        <h3>My requests</h3>
        <table className="tbl">
          <thead><tr>
            <th>#</th>
            <th>Type</th>
            <th>Dates</th>
            <th>Days</th>
            <th>Status</th>
            <th>Approval Chain</th>
            <th>Conflict</th>
            <th></th>
          </tr></thead>
          <tbody>
            {list.length === 0 && <tr><td colSpan="8" className="empty">No requests yet — apply above.</td></tr>}
            {list.map(v => (
              <tr key={v.id}>
                <td>{v.id}</td>
                <td>{v.leaveTypeCode}</td>
                <td>{dates(v)}</td>
                <td>{v.days}</td>
                <td><StatusPill status={v.status} /></td>
                <td><ApprovalProgress status={v.status} /></td>
                <td><ConflictBadge v={v} /></td>
                <td>
                  <button className="btn small ghost" onClick={() => showHistory(v.id)}>History</button>
                  {v.status.startsWith('PENDING') &&
                    <button className="btn small reject" onClick={() => cancel(v.id)}>Cancel</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

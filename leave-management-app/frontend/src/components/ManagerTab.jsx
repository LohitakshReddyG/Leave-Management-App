import React, { useEffect, useState } from 'react'
import { api, fmtWhen } from '../api.js'
import { StatusPill, ApprovalProgress, ConflictBadge, dates } from './EmployeeTab.jsx'

/* Approve / reject with a comment, shared by the manager and HR queues. */
export function DecisionButtons({ v, me, notify, refresh, isManager }) {
  async function decide(action) {
    const promptMsg = action === 'approve'
      ? (isManager ? 'Comment for manager approval (advances request to HR):' : 'Comment for HR final approval:')
      : `Comment for this ${action} (optional):`
    const comment = window.prompt(promptMsg) || ''
    try {
      const r = await api('POST', `/api/requests/${v.id}/${action}`, { actorId: me.id, comment })
      const nextStageMsg = action === 'approve'
        ? (isManager ? 'Approved and forwarded to Stage 2 (HR Final Approval).' : 'Final approval granted.')
        : 'Request rejected.'
      notify(`Request #${v.id} → ${r.status.replace(/_/g, ' ')}. ${nextStageMsg}`, 'ok')
      refresh()
    } catch (err) { notify(err.message, 'err') }
  }
  return (
    <div style={{ display: 'flex', gap: '6px' }}>
      <button className="btn small approve" onClick={() => decide('approve')}>
        {isManager ? 'Approve → HR' : 'Final Approve'}
      </button>
      <button className="btn small reject" onClick={() => decide('reject')}>Reject</button>
    </div>
  )
}

export function QueueTable({ list, me, notify, refresh, emptyText, isManager }) {
  return (
    <table className="tbl">
      <thead><tr>
        <th>#</th><th>Employee</th><th>Days</th><th>Dates</th><th>Current Status</th><th>Approval Chain</th><th>Conflict</th><th>Decision</th>
      </tr></thead>
      <tbody>
        {list.length === 0 && <tr><td colSpan="8" className="empty">{emptyText}</td></tr>}
        {list.map(v => (
          <tr key={v.id}>
            <td>{v.id}</td>
            <td><b>{v.employeeName}</b></td>
            <td>{v.days}</td>
            <td>{dates(v)}</td>
            <td><StatusPill status={v.status} /></td>
            <td><ApprovalProgress status={v.status} /></td>
            <td><ConflictBadge v={v} /></td>
            <td><DecisionButtons v={v} me={me} notify={notify} refresh={refresh} isManager={isManager} /></td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export function Feed({ items }) {
  if (!items.length) return <ul className="feed"><li className="empty">No notifications.</li></ul>
  return (
    <ul className="feed">
      {items.map(n => (
        <li key={n.id} className={n.type}>
          <b>{n.type}</b> — request #{n.requestId}: {n.message}
          <span className="meta">{fmtWhen(n.createdAt)}</span>
        </li>
      ))}
    </ul>
  )
}

export default function ManagerTab({ me, tick, notify, refresh }) {
  const [list, setList] = useState([])
  const [feed, setFeed] = useState([])

  useEffect(() => {
    api('GET', `/api/requests/pending/manager?managerId=${me.id}`).then(setList).catch(() => {})
    api('GET', `/api/notifications?managerId=${me.id}`).then(setFeed).catch(() => {})
  }, [me.id, tick])

  return (
    <>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Stage 1: Team Manager Pending Approvals {list.length ? `(${list.length})` : ''}</h3>
          <span className="portal-stage-badge">Stage 2: Manager Review</span>
        </div>
        <p className="hint">
          As Team Manager, your approval completes <b>Stage 1</b> and forwards the request to <b>Stage 2 (HR Final Approval)</b>.
          Conflict-flagged requests show who is already off — they are flagged for you, never auto-rejected.
        </p>
        <div style={{ marginTop: '12px' }}>
          <QueueTable list={list} me={me} notify={notify} refresh={refresh} emptyText="No pending team requests." isManager={true} />
        </div>
      </div>
      <div className="card">
        <h3>Manager Notifications</h3>
        <Feed items={feed} />
      </div>
    </>
  )
}

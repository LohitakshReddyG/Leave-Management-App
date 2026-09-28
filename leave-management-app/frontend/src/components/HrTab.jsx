import React, { useEffect, useState } from 'react'
import { api, fmtWhen } from '../api.js'
import { QueueTable, Feed } from './ManagerTab.jsx'

export default function HrTab({ me, tick, notify, refresh }) {
  const [list, setList] = useState([])
  const [feed, setFeed] = useState([])

  useEffect(() => {
    api('GET', '/api/requests/pending/hr').then(setList).catch(() => {})
    api('GET', '/api/notifications?hr=true').then(setFeed).catch(() => {})
  }, [tick])

  const escalations = feed.filter(n => n.type === 'ESCALATION')

  return (
    <>
      {escalations.length > 0 && (
        <div className="escalation-banner">
          <b>ESCALATION NOTICE:</b> {escalations.length} request{escalations.length > 1 ? 's' : ''} escalated due to manager timeout: {' '}
          {escalations.map(n => `Request #${n.requestId} (${fmtWhen(n.createdAt)})`).join(' · ')}
          <div style={{ fontSize: '13px', marginTop: '4px', opacity: 0.9 }}>
            Manager did not respond within deadline; these requests bypassed manager approval and are awaiting HR decision directly.
          </div>
        </div>
      )}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Stage 2: HR Final Stage Approvals {list.length ? `(${list.length})` : ''}</h3>
          <span className="portal-stage-badge">Stage 3: HR Final Authority</span>
        </div>
        <p className="hint">
          These requests have already received <b>Manager Approval (Stage 1)</b> or were <b>auto-escalated</b>.
          Approving here marks the leave fully <b>APPROVED</b> in accordance with company policy.
        </p>
        <div style={{ marginTop: '12px' }}>
          <QueueTable list={list} me={me} notify={notify} refresh={refresh} emptyText="No pending second-stage requests." isManager={false} />
        </div>
      </div>
      <div className="card">
        <h3>Escalations & Conflict Audit Trail</h3>
        <Feed items={feed} />
      </div>
    </>
  )
}

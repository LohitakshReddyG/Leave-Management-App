import React, { useEffect, useState } from 'react'
import { api, fmtWhen } from '../api.js'

/* The state machine's visual proof: the full transition timeline of a request. */
export default function HistoryModal({ id, onClose }) {
  const [history, setHistory] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api('GET', `/api/requests/${id}/history`).then(setHistory).catch(e => setError(e.message))
  }, [id])

  const latest = history && history.length > 0 ? history[history.length - 1].toStatus : null
  const isMgrApproved = history && history.some(h => h.toStatus === 'PENDING_HR' || h.toStatus === 'APPROVED')
  const isHrApproved = latest === 'APPROVED'
  const isRejected = latest && latest.startsWith('REJECTED')

  return (
    <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal">
        <div className="modal-head">
          <div>
            <h3>Leave Request Audit Timeline #{id}</h3>
            <span style={{ fontSize: '13px', color: 'var(--muted)' }}>Hierarchy: Employee → Manager → HR</span>
          </div>
          <button className="btn ghost" onClick={onClose}>Close</button>
        </div>

        {/* Modal Mini-Chain without emojis */}
        <div className="modal-hierarchy-chain">
          <div className="m-step done">
            <span className="m-dot">1</span>
            <span className="m-name">Employee Applied</span>
          </div>
          <div className="m-arrow">→</div>
          <div className={`m-step ${isMgrApproved ? 'done' : isRejected ? 'fail' : 'active'}`}>
            <span className="m-dot">2</span>
            <span className="m-name">Manager Review {isMgrApproved ? '[Passed]' : isRejected ? '[Rejected]' : '[Pending]'}</span>
          </div>
          <div className="m-arrow">→</div>
          <div className={`m-step ${isHrApproved ? 'done' : isRejected ? 'fail' : isMgrApproved ? 'active' : 'idle'}`}>
            <span className="m-dot">3</span>
            <span className="m-name">HR Sign-off {isHrApproved ? '[Approved]' : isRejected ? '[Rejected]' : isMgrApproved ? '[Pending]' : ''}</span>
          </div>
        </div>

        {error && <p className="hint">{error}</p>}
        {history && (
          <ol className="timeline">
            {history.map((h, i) => (
              <li key={i}>
                <div className="move">
                  {h.fromStatus ? h.fromStatus.replace(/_/g, ' ') : 'INIT'}
                  <span className="arr"> → </span>
                  {h.toStatus.replace(/_/g, ' ')}
                </div>
                <div className="sub">
                  {h.comment || '(no comment)'} · by <b>{h.actorName}</b> · {fmtWhen(h.at)}
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  )
}

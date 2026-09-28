import React from 'react'

export default function HierarchyBanner({ currentRole }) {
  const steps = [
    { num: '1', role: 'EMPLOYEE', label: 'Employee', action: 'Applies for Leave' },
    { num: '2', role: 'MANAGER', label: 'Manager', action: '1st-Tier Review & Conflict Check' },
    { num: '3', role: 'HR', label: 'HR Admin', action: 'Final Approval & Policy Sign-off' },
  ]

  return (
    <div className="hierarchy-banner">
      <div className="hierarchy-title">
        <span className="hierarchy-indicator-dot"></span>
        <span><b>Leave Approval Chain:</b> HR → MANAGER → EMPLOYEE (Employee Submits → Manager Reviews → HR Final Approval)</span>
      </div>
      <div className="hierarchy-steps">
        {steps.map((s, idx) => {
          const isActive = currentRole === s.role
          return (
            <React.Fragment key={s.num}>
              <div className={`h-step ${isActive ? 'active' : ''}`}>
                <div className="h-step-badge">{s.num}</div>
                <div className="h-step-info">
                  <div className="h-step-label">{s.label}</div>
                  <div className="h-step-action">{s.action}</div>
                </div>
              </div>
              {idx < steps.length - 1 && <div className="h-step-arrow">→</div>}
            </React.Fragment>
          )
        })}
      </div>
    </div>
  )
}

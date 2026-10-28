import React, { useState, useEffect, useCallback } from 'react';
import { getMyLeaves, getLeaveBalance, LeaveRequest, LeaveBalance } from './api';

interface MyRequestsProps {
  currentEmail: string;
}

export const MyRequests: React.FC<MyRequestsProps> = ({ currentEmail }) => {
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [balance, setBalance] = useState<LeaveBalance | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    if (!currentEmail || !currentEmail.includes('@')) return;
    try {
      setLoading(true);
      setErrorMsg(null);
      const [leavesData, balanceData] = await Promise.all([
        getMyLeaves(currentEmail),
        getLeaveBalance(currentEmail)
      ]);
      setRequests(leavesData);
      setBalance(balanceData);
    } catch {
      setErrorMsg('Could not fetch leave history. Please ensure backend is running.');
    } finally {
      setLoading(false);
    }
  }, [currentEmail]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="glass-card">
      <div className="card-header">
        <div>
          <h2 className="card-title">My Leave Applications</h2>
          <p className="card-desc">History and real-time status of all your submitted leave requests.</p>
        </div>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          {balance && (
            <div className="balance-widget" id="my-requests-balance-badge">
              <div>
                <div className="balance-number">{balance.remainingDays} / {balance.annualEntitlement}</div>
                <div className="balance-label">Annual Leave Remaining</div>
              </div>
            </div>
          )}
          <button
            id="btn-refresh-my-requests"
            className="btn btn-secondary"
            onClick={fetchData}
            disabled={loading}
            title="Refresh leave requests"
          >
            {loading ? 'Refreshing...' : '🔄 Refresh'}
          </button>
        </div>
      </div>

      {errorMsg && (
        <div className="alert-banner error" role="alert">
          <span>⚠️</span>
          <div>{errorMsg}</div>
        </div>
      )}

      {loading && requests.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">⏳</div>
          <div className="empty-title">Loading your leave requests...</div>
        </div>
      ) : requests.length === 0 ? (
        <div className="empty-state" id="empty-my-requests">
          <div className="empty-icon">🏖️</div>
          <div className="empty-title">No leave applications found</div>
          <p>You have not submitted any leave applications yet under this email.</p>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="custom-table" id="my-requests-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Leave Type</th>
                <th>Date Range</th>
                <th>Working Days</th>
                <th>Reason</th>
                <th>Status</th>
                <th>Status Message / Comments</th>
              </tr>
            </thead>
            <tbody>
              {requests.map(req => (
                <tr key={req.id}>
                  <td><strong>#{req.id}</strong></td>
                  <td>
                    <span className="badge badge-type">{req.leaveType}</span>
                  </td>
                  <td>
                    <div>{formatDate(req.startDate)} ➔ {formatDate(req.endDate)}</div>
                  </td>
                  <td>
                    <strong>{req.workingDays}</strong> {req.workingDays === 1 ? 'day' : 'days'}
                  </td>
                  <td style={{ maxWidth: '220px', wordBreak: 'break-word' }}>
                    {req.reason}
                  </td>
                  <td>
                    <span className={`badge badge-${req.status}`} id={`status-badge-${req.id}`}>
                      {req.status.replace('_', ' ')}
                    </span>
                  </td>
                  <td style={{ maxWidth: '260px', color: 'var(--text-muted)' }}>
                    <div>{req.statusMessage || '—'}</div>
                    {req.managerComment && (
                      <div style={{ marginTop: '4px', fontSize: '0.8rem', color: '#cbd5e1' }}>
                        <em>Manager: "{req.managerComment}"</em>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

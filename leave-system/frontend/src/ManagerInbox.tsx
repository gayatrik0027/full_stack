import React, { useState, useEffect, useCallback } from 'react';
import { getPendingLeaves, submitDecision, LeaveRequest, ApiError } from './api';

interface ManagerInboxProps {
  onDecisionCompleted: () => void;
}

export const ManagerInbox: React.FC<ManagerInboxProps> = ({ onDecisionCompleted }) => {
  const [pendingRequests, setPendingRequests] = useState<LeaveRequest[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Selected request for decision dialog
  const [activeModalRequest, setActiveModalRequest] = useState<LeaveRequest | null>(null);
  const [modalApproved, setModalApproved] = useState<boolean>(true);
  const [modalComment, setModalComment] = useState<string>('');

  const fetchPending = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      const data = await getPendingLeaves();
      setPendingRequests(data);
    } catch {
      setErrorMsg('Could not load manager inbox. Please check backend connection.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPending();
  }, [fetchPending]);

  const openDecisionModal = (req: LeaveRequest, approved: boolean) => {
    setActiveModalRequest(req);
    setModalApproved(approved);
    setModalComment(approved ? 'Approved by manager.' : 'Rejected due to project staffing constraints.');
  };

  const handleDecisionSubmit = async () => {
    if (!activeModalRequest) return;
    try {
      setActionLoading(activeModalRequest.id);
      setErrorMsg(null);
      setSuccessMsg(null);

      await submitDecision(activeModalRequest.id, {
        approved: modalApproved,
        comment: modalComment.trim() || undefined,
      });

      setSuccessMsg(`Decision successfully submitted for Leave Request #${activeModalRequest.id} (${modalApproved ? 'APPROVED' : 'REJECTED'}).`);
      setActiveModalRequest(null);
      await fetchPending();
      onDecisionCompleted();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      if (apiErr.status === 409) {
        setErrorMsg(`Conflict: The approval task for request #${activeModalRequest.id} is not yet available in the workflow engine or has already been decided.`);
      } else {
        setErrorMsg(apiErr.message || 'Failed to submit approval decision.');
      }
    } finally {
      setActionLoading(null);
    }
  };

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
          <h2 className="card-title">Manager Approval Inbox</h2>
          <p className="card-desc">Review and decide on pending leave applications submitted by employees.</p>
        </div>
        <button
          id="btn-refresh-manager-inbox"
          className="btn btn-secondary"
          onClick={fetchPending}
          disabled={loading}
          title="Refresh pending requests"
        >
          {loading ? 'Refreshing...' : '🔄 Refresh Inbox'}
        </button>
      </div>

      {successMsg && (
        <div className="alert-banner success" role="alert" id="manager-success-banner">
          <span>✅</span>
          <div>{successMsg}</div>
        </div>
      )}

      {errorMsg && (
        <div className="alert-banner error" role="alert" id="manager-error-banner">
          <span>⚠️</span>
          <div>{errorMsg}</div>
        </div>
      )}

      {loading && pendingRequests.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">⏳</div>
          <div className="empty-title">Loading pending approvals...</div>
        </div>
      ) : pendingRequests.length === 0 ? (
        <div className="empty-state" id="empty-manager-inbox">
          <div className="empty-icon">🎉</div>
          <div className="empty-title">All caught up!</div>
          <p>There are no leave applications currently awaiting manager decision.</p>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="custom-table" id="manager-inbox-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Employee</th>
                <th>Leave Type</th>
                <th>Requested Dates</th>
                <th>Working Days</th>
                <th>Reason</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pendingRequests.map(req => (
                <tr key={req.id}>
                  <td><strong>#{req.id}</strong></td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{req.employeeName}</div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{req.employeeEmail}</div>
                  </td>
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
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        id={`btn-approve-${req.id}`}
                        className="btn btn-success"
                        style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                        onClick={() => openDecisionModal(req, true)}
                        disabled={actionLoading === req.id}
                      >
                        Approve
                      </button>
                      <button
                        id={`btn-reject-${req.id}`}
                        className="btn btn-danger"
                        style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                        onClick={() => openDecisionModal(req, false)}
                        disabled={actionLoading === req.id}
                      >
                        Reject
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Decision Confirmation Modal */}
      {activeModalRequest && (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal-content">
            <div className="modal-header">
              <h3 className="modal-title">
                {modalApproved ? '✅ Approve Leave Request' : '❌ Reject Leave Request'}
              </h3>
              <button
                className="close-btn"
                onClick={() => setActiveModalRequest(null)}
                aria-label="Close modal"
              >
                ✕
              </button>
            </div>

            <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
              Confirm your decision for request <strong>#{activeModalRequest.id}</strong> submitted by{' '}
              <strong>{activeModalRequest.employeeName}</strong> ({activeModalRequest.workingDays} working days).
            </p>

            <div className="form-group" style={{ marginBottom: '16px' }}>
              <label className="form-label" htmlFor="modalComment">
                Manager Comment (Optional)
              </label>
              <textarea
                id="modalComment"
                className="form-textarea"
                rows={3}
                placeholder="Add optional notes or justification..."
                value={modalComment}
                onChange={e => setModalComment(e.target.value)}
              />
            </div>

            <div className="modal-actions">
              <button
                className="btn btn-secondary"
                onClick={() => setActiveModalRequest(null)}
                disabled={actionLoading === activeModalRequest.id}
              >
                Cancel
              </button>
              <button
                id="btn-confirm-decision"
                className={`btn ${modalApproved ? 'btn-success' : 'btn-danger'}`}
                onClick={handleDecisionSubmit}
                disabled={actionLoading === activeModalRequest.id}
              >
                {actionLoading === activeModalRequest.id
                  ? 'Submitting...'
                  : modalApproved ? 'Confirm Approval' : 'Confirm Rejection'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

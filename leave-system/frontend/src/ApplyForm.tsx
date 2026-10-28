import React, { useState, useEffect } from 'react';
import {
  submitLeave,
  getLeaveBalance,
  calculateWorkingDaysClient,
  LeaveType,
  LeaveBalance,
  ApiError
} from './api';

interface ApplyFormProps {
  currentEmail: string;
  onSuccess: () => void;
}

export const ApplyForm: React.FC<ApplyFormProps> = ({ currentEmail, onSuccess }) => {
  const [employeeName, setEmployeeName] = useState('Alice Smith');
  const [employeeEmail, setEmployeeEmail] = useState(currentEmail);
  const [leaveType, setLeaveType] = useState<LeaveType>('ANNUAL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [reason, setReason] = useState('');

  const [balance, setBalance] = useState<LeaveBalance | null>(null);
  const [workingDays, setWorkingDays] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Sync email when currentEmail changes
  useEffect(() => {
    setEmployeeEmail(currentEmail);
  }, [currentEmail]);

  // Fetch balance whenever email changes
  useEffect(() => {
    if (employeeEmail && employeeEmail.includes('@')) {
      getLeaveBalance(employeeEmail)
        .then(b => setBalance(b))
        .catch(() => setBalance(null));
    }
  }, [employeeEmail]);

  // Recalculate working days whenever dates change
  useEffect(() => {
    if (startDate && endDate) {
      const days = calculateWorkingDaysClient(startDate, endDate);
      setWorkingDays(days);
    } else {
      setWorkingDays(0);
    }
  }, [startDate, endDate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);

    // Client-side validations
    if (!employeeName.trim()) {
      setErrorMsg('Employee name is required.');
      return;
    }
    if (!employeeEmail.trim() || !employeeEmail.includes('@')) {
      setErrorMsg('A valid employee email address is required.');
      return;
    }
    if (!startDate || !endDate) {
      setErrorMsg('Please select both start date and end date.');
      return;
    }
    if (new Date(endDate) < new Date(startDate)) {
      setErrorMsg('End date cannot be earlier than start date.');
      return;
    }
    if (workingDays <= 0) {
      setErrorMsg('The selected date range contains 0 working days (e.g. only falls on weekends).');
      return;
    }
    if (!reason.trim()) {
      setErrorMsg('Please provide a reason for the leave request.');
      return;
    }

    if (leaveType === 'ANNUAL' && balance && balance.remainingDays < workingDays) {
      setErrorMsg(`Insufficient annual leave balance! You requested ${workingDays} working days, but only have ${balance.remainingDays} days remaining.`);
      return;
    }

    try {
      setLoading(true);
      const created = await submitLeave({
        employeeName: employeeName.trim(),
        employeeEmail: employeeEmail.trim(),
        leaveType,
        startDate,
        endDate,
        reason: reason.trim(),
      });

      setSuccessMsg(`Leave request #${created.id} submitted successfully! Status: ${created.status} (Tracking key: ${created.processInstanceKey || 'Local'}).`);
      setReason('');
      setStartDate('');
      setEndDate('');
      setWorkingDays(0);

      // Refresh balance
      getLeaveBalance(employeeEmail).then(b => setBalance(b));
      onSuccess();
    } catch (err: unknown) {
      const apiErr = err as ApiError;
      setErrorMsg(apiErr.message || 'Failed to submit leave request. Please check backend connection.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="glass-card">
      <div className="card-header">
        <div>
          <h2 className="card-title">Apply for Leave</h2>
          <p className="card-desc">Submit your leave request for automated workflow validation and manager approval.</p>
        </div>
        {balance && (
          <div className="balance-widget" id="leave-balance-badge">
            <div>
              <div className="balance-number">{balance.remainingDays} / {balance.annualEntitlement}</div>
              <div className="balance-label">Annual Leave Days Remaining</div>
            </div>
          </div>
        )}
      </div>

      {successMsg && (
        <div className="alert-banner success" role="alert" id="submit-success-banner">
          <span>✅</span>
          <div>{successMsg}</div>
        </div>
      )}

      {errorMsg && (
        <div className="alert-banner error" role="alert" id="submit-error-banner">
          <span>⚠️</span>
          <div>{errorMsg}</div>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <div className="form-grid">
          <div className="form-group">
            <label className="form-label" htmlFor="employeeName">
              Full Name <span className="req">*</span>
            </label>
            <input
              id="employeeName"
              className="form-input"
              type="text"
              placeholder="e.g. Alice Smith"
              value={employeeName}
              onChange={e => setEmployeeName(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="employeeEmail">
              Email Address <span className="req">*</span>
            </label>
            <input
              id="employeeEmail"
              className="form-input"
              type="email"
              placeholder="e.g. alice@example.com"
              value={employeeEmail}
              onChange={e => setEmployeeEmail(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="leaveType">
              Leave Type <span className="req">*</span>
            </label>
            <select
              id="leaveType"
              className="form-select"
              value={leaveType}
              onChange={e => setLeaveType(e.target.value as LeaveType)}
            >
              <option value="ANNUAL">Annual Leave (Deducted upon approval)</option>
              <option value="SICK">Sick Leave (Medical reason)</option>
              <option value="UNPAID">Unpaid Leave</option>
            </select>
            <span className="form-hint">
              {leaveType === 'ANNUAL' ? 'Deducts from your 20-day annual allowance upon approval.' : 'Does not affect annual leave balance.'}
            </span>
          </div>

          <div className="form-group">
            <label className="form-label">Calculated Working Days</label>
            <div className="calc-preview-box">
              <span className="calc-preview-text">Mon – Fri (Excludes Sat & Sun)</span>
              <span className="calc-preview-value" id="calc-working-days-display">{workingDays} {workingDays === 1 ? 'day' : 'days'}</span>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="startDate">
              Start Date <span className="req">*</span>
            </label>
            <input
              id="startDate"
              className="form-input"
              type="date"
              value={startDate}
              onChange={e => setStartDate(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="endDate">
              End Date <span className="req">*</span>
            </label>
            <input
              id="endDate"
              className="form-input"
              type="date"
              value={endDate}
              onChange={e => setEndDate(e.target.value)}
              required
            />
          </div>

          <div className="form-group form-full">
            <label className="form-label" htmlFor="reason">
              Reason for Leave <span className="req">*</span>
            </label>
            <textarea
              id="reason"
              className="form-textarea"
              placeholder="Provide a brief explanation for your leave request..."
              value={reason}
              onChange={e => setReason(e.target.value)}
              required
            />
          </div>
        </div>

        <div style={{ marginTop: '28px', display: 'flex', justifyContent: 'flex-end' }}>
          <button
            id="btn-submit-leave"
            type="submit"
            className="btn btn-primary"
            disabled={loading}
          >
            {loading ? 'Submitting Application...' : 'Submit Leave Application'}
          </button>
        </div>
      </form>
    </div>
  );
};
